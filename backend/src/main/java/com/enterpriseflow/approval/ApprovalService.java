package com.enterpriseflow.approval;

import com.enterpriseflow.approval.dto.ApprovalDtos.ApprovalResponse;
import com.enterpriseflow.approval.dto.ApprovalDtos.Ref;
import com.enterpriseflow.approval.dto.ApprovalDtos.Scope;
import com.enterpriseflow.approval.dto.ApprovalDtos.SubmitApprovalRequest;
import com.enterpriseflow.audit.AuditAction;
import com.enterpriseflow.audit.AuditEntityType;
import com.enterpriseflow.audit.AuditService;
import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.common.error.BadRequestException;
import com.enterpriseflow.common.error.ConflictException;
import com.enterpriseflow.common.error.ResourceNotFoundException;
import com.enterpriseflow.notification.NotificationService;
import com.enterpriseflow.notification.NotificationType;
import com.enterpriseflow.project.Project;
import com.enterpriseflow.project.ProjectAccessService;
import com.enterpriseflow.project.ProjectMember;
import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.task.Task;
import com.enterpriseflow.task.TaskService;
import com.enterpriseflow.task.TaskStatus;
import com.enterpriseflow.user.User;
import com.enterpriseflow.user.UserRepository;
import com.enterpriseflow.user.UserService;
import com.enterpriseflow.user.dto.UserSummary;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Approval workflow. A decision and its business effect (task completed, budget changed), the audit entry and
 * the requester's notification are written in one transaction: either all of them happen or none.
 */
@Service
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final ProjectAccessService access;
    private final TaskService taskService;
    private final UserService userService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final Clock clock;

    public ApprovalService(ApprovalRepository approvalRepository, ProjectAccessService access, TaskService taskService,
                           UserService userService, UserRepository userRepository, AuditService auditService,
                           NotificationService notificationService, Clock clock) {
        this.approvalRepository = approvalRepository;
        this.access = access;
        this.taskService = taskService;
        this.userService = userService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    // ---- Submit -----------------------------------------------------------------------------------------

    /**
     * Creates a request after the type-specific checks, records it in the audit trail and notifies the people
     * who can decide it (the project manager for task completion, all active admins for budget changes).
     */
    @Transactional
    public ApprovalResponse submit(CurrentUser actor, SubmitApprovalRequest request) {
        User requester = userService.findWithRoles(actor.id());
        ApprovalRequest approval = switch (request.type()) {
            case TASK_COMPLETION -> taskCompletion(actor, requester, request);
            case PROJECT_BUDGET_CHANGE -> budgetChange(actor, requester, request);
        };
        // The partial unique index (V4) is the final guard if two identical requests race past the check above.
        approvalRepository.saveAndFlush(approval);

        auditService.record(actor.id(), AuditAction.APPROVAL_SUBMITTED, AuditEntityType.APPROVAL, approval.getId(),
                approval.getProject().getId(), Map.of("type", approval.getType()));
        notifyReviewers(approval);
        return toResponse(approval, actor);
    }

    /**
     * Rules for a completion request: only the assignee or the project manager may ask, the task must be marked
     * as requiring approval, it must already be IN_REVIEW, and only one request per task may be pending.
     */
    private ApprovalRequest taskCompletion(CurrentUser actor, User requester, SubmitApprovalRequest request) {
        if (request.taskId() == null || request.requestedBudget() != null) {
            throw new BadRequestException("A task completion request needs a taskId and no requestedBudget.");
        }
        Task task = taskService.requireViewable(request.taskId(), actor);
        task.getProject().ensureNotArchived();
        if (!task.isAssignedTo(actor.id()) && !access.canManage(task.getProject(), actor)) {
            throw new AccessDeniedException("Only the assignee or the project manager can request completion.");
        }
        if (!task.isRequiresApproval()) {
            throw new BadRequestException("This task does not require approval; complete it directly.");
        }
        if (task.getStatus() != TaskStatus.IN_REVIEW) {
            throw new ConflictException("Move the task to IN_REVIEW before requesting completion.");
        }
        if (approvalRepository.existsByTypeAndTaskIdAndStatus(ApprovalType.TASK_COMPLETION, task.getId(),
                ApprovalStatus.PENDING)) {
            throw new ConflictException("A completion request for this task is already pending.");
        }
        return ApprovalRequest.taskCompletion(task, requester, request.reason());
    }

    /**
     * Rules for a budget change: only someone who manages the project may ask (a budget is a management decision),
     * the project must not be archived, and only one budget request per project may be pending.
     */
    private ApprovalRequest budgetChange(CurrentUser actor, User requester, SubmitApprovalRequest request) {
        if (request.projectId() == null || request.requestedBudget() == null || request.taskId() != null) {
            throw new BadRequestException("A budget change needs a projectId and a requestedBudget, and no taskId.");
        }
        Project project = access.requireManageable(request.projectId(), actor);
        project.ensureNotArchived();
        if (approvalRepository.existsByTypeAndProjectIdAndStatus(ApprovalType.PROJECT_BUDGET_CHANGE, project.getId(),
                ApprovalStatus.PENDING)) {
            throw new ConflictException("A budget change for this project is already pending.");
        }
        return ApprovalRequest.budgetChange(project, request.requestedBudget(), requester, request.reason());
    }

    // ---- Decide -----------------------------------------------------------------------------------------

    /**
     * Approves a pending request and applies what it asked for. If two reviewers approve at the same time, the
     * optimistic lock on the request lets only the first commit succeed; the second gets 409.
     */
    @Transactional
    public ApprovalResponse approve(UUID id, CurrentUser actor, String comment) {
        ApprovalRequest approval = requireDecidable(id, actor);
        User reviewer = userService.findWithRoles(actor.id());

        // The business effect is applied in the same transaction as the decision.
        switch (approval.getType()) {
            case TASK_COMPLETION -> taskService.completeByApproval(approval.getTask(), actor.id());
            case PROJECT_BUDGET_CHANGE -> approval.getProject().changeBudget(approval.getRequestedBudget());
        }
        approval.approve(reviewer, comment, clock.instant());

        recordDecision(approval, actor, AuditAction.APPROVAL_APPROVED);
        return toResponse(approval, actor);
    }

    /**
     * Rejects a pending request. A comment is mandatory so the requester knows what to fix. Nothing else changes:
     * a rejected task stays IN_REVIEW and can be reworked and submitted again.
     */
    @Transactional
    public ApprovalResponse reject(UUID id, CurrentUser actor, String comment) {
        if (comment == null || comment.isBlank()) {
            throw new BadRequestException("A comment explaining the rejection is required.");
        }
        ApprovalRequest approval = requireDecidable(id, actor);
        approval.reject(userService.findWithRoles(actor.id()), comment, clock.instant());
        recordDecision(approval, actor, AuditAction.APPROVAL_REJECTED);
        return toResponse(approval, actor);
    }

    /** Lets the requester withdraw their own pending request, e.g. when it was submitted by mistake. */
    @Transactional
    public ApprovalResponse cancel(UUID id, CurrentUser actor) {
        ApprovalRequest approval = requireVisible(id, actor);
        if (!approval.getRequester().getId().equals(actor.id())) {
            throw new AccessDeniedException("Only the requester can cancel a request.");
        }
        approval.cancel();
        auditService.record(actor.id(), AuditAction.APPROVAL_CANCELLED, AuditEntityType.APPROVAL, id,
                approval.getProject().getId(), Map.of("type", approval.getType()));
        return toResponse(approval, actor);
    }

    /**
     * Check order matters: state first (409 for a duplicate decision is more useful than 403), then authority,
     * then the self-approval rule.
     */
    private ApprovalRequest requireDecidable(UUID id, CurrentUser actor) {
        ApprovalRequest approval = requireVisible(id, actor);
        if (approval.getStatus().isFinal()) {
            throw new ConflictException("This request is already %s.".formatted(approval.getStatus().name().toLowerCase()));
        }
        if (!isReviewer(approval, actor)) {
            throw new AccessDeniedException("You are not allowed to decide this request.");
        }
        if (approval.getRequester().getId().equals(actor.id())) {
            throw new AccessDeniedException("You cannot approve or reject your own request.");
        }
        return approval;
    }

    /** Audit entry for the decision plus a notification to the requester with the outcome. */
    private void recordDecision(ApprovalRequest approval, CurrentUser actor, AuditAction action) {
        auditService.record(actor.id(), action, AuditEntityType.APPROVAL, approval.getId(),
                approval.getProject().getId(), Map.of("type", approval.getType(),
                        "comment", approval.getReviewComment() == null ? "" : approval.getReviewComment()));

        String outcome = approval.getStatus() == ApprovalStatus.APPROVED ? "approved" : "rejected";
        notificationService.notify(approval.getRequester().getId(), NotificationType.APPROVAL_DECIDED,
                "Request " + outcome,
                "Your %s request in %s was %s.".formatted(label(approval.getType()), approval.getProject().getName(),
                        outcome),
                "APPROVAL", approval.getId());
    }

    // ---- Queries ----------------------------------------------------------------------------------------

    /** Paged list for one of the three tabs of the approvals page (mine / to review / all visible). */
    @Transactional(readOnly = true)
    public PageResponse<ApprovalResponse> list(CurrentUser actor, Scope scope, ApprovalStatus status,
                                               Pageable pageable) {
        return PageResponse.from(approvalRepository.findAll(scopeSpec(actor, scope, status), pageable),
                a -> toResponse(a, actor));
    }

    /** One request, if the caller may see it. */
    @Transactional(readOnly = true)
    public ApprovalResponse get(UUID id, CurrentUser actor) {
        return toResponse(requireVisible(id, actor), actor);
    }

    /**
     * Visible to admins, to the requester and to members of the request's project. Anyone else gets 404, the same
     * as for a request that does not exist, so ids cannot be probed.
     */
    private ApprovalRequest requireVisible(UUID id, CurrentUser actor) {
        ApprovalRequest approval = approvalRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request", id));
        boolean visible = actor.isAdmin()
                || approval.getRequester().getId().equals(actor.id())
                || access.isMember(approval.getProject().getId(), actor.id());
        if (!visible) {
            throw new ResourceNotFoundException("Approval request", id);
        }
        return approval;
    }

    // ---- Rules & mapping --------------------------------------------------------------------------------

    /** TASK_COMPLETION: project manager or ADMIN. PROJECT_BUDGET_CHANGE: ADMIN only. */
    private boolean isReviewer(ApprovalRequest approval, CurrentUser actor) {
        return switch (approval.getType()) {
            case TASK_COMPLETION -> actor.isAdmin() || approval.getProject().isManagedBy(actor.id());
            case PROJECT_BUDGET_CHANGE -> actor.isAdmin();
        };
    }

    /** Notifies everyone who can decide the request, except the requester themselves. */
    private void notifyReviewers(ApprovalRequest approval) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (approval.getType() == ApprovalType.TASK_COMPLETION) {
            recipients.add(approval.getProject().getManager().getId());
        } else {
            userRepository.findActiveAdmins().forEach(admin -> recipients.add(admin.getId()));
        }
        recipients.remove(approval.getRequester().getId());
        for (UUID recipient : recipients) {
            notificationService.notify(recipient, NotificationType.APPROVAL_REQUESTED, "Approval requested",
                    "%s requested %s approval in %s.".formatted(approval.getRequester().getFullName(),
                            label(approval.getType()), approval.getProject().getName()),
                    "APPROVAL", approval.getId());
        }
    }

    private static String label(ApprovalType type) {
        return type == ApprovalType.TASK_COMPLETION ? "task completion" : "budget change";
    }

    /**
     * Maps to the API response. canDecide / canCancel are computed with the same rules the actions enforce, so
     * the UI shows only buttons that will actually work for this user.
     */
    private ApprovalResponse toResponse(ApprovalRequest a, CurrentUser actor) {
        boolean pending = a.getStatus() == ApprovalStatus.PENDING;
        boolean ownRequest = a.getRequester().getId().equals(actor.id());
        Ref task = a.getTask() == null ? null : new Ref(a.getTask().getId(), a.getTask().getTitle());
        return new ApprovalResponse(a.getId(), a.getType(), a.getStatus(),
                new Ref(a.getProject().getId(), a.getProject().getName()), task,
                UserSummary.from(a.getRequester()), UserSummary.from(a.getReviewer()), a.getReason(),
                a.getRequestedBudget(), a.getProject().getBudget(), a.getReviewComment(), a.getDecidedAt(),
                a.getCreatedAt(), pending && !ownRequest && isReviewer(a, actor), pending && ownRequest);
    }

    /** WHERE clause for each list scope, always limited to what the caller is allowed to see. */
    private static Specification<ApprovalRequest> scopeSpec(CurrentUser actor, Scope scope, ApprovalStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            switch (scope) {
                case MINE -> predicates.add(cb.equal(root.get("requester").get("id"), actor.id()));
                case TO_REVIEW -> {
                    predicates.add(cb.equal(root.get("status"), ApprovalStatus.PENDING));
                    predicates.add(cb.notEqual(root.get("requester").get("id"), actor.id()));
                    if (!actor.isAdmin()) {
                        // Non-admins review only completion requests of projects they manage.
                        predicates.add(cb.equal(root.get("type"), ApprovalType.TASK_COMPLETION));
                        predicates.add(cb.equal(root.get("project").get("manager").get("id"), actor.id()));
                    }
                }
                case ALL -> {
                    if (!actor.isAdmin()) {
                        Subquery<UUID> membership = query.subquery(UUID.class);
                        Root<ProjectMember> member = membership.from(ProjectMember.class);
                        membership.select(member.get("id")).where(
                                cb.equal(member.get("project"), root.get("project")),
                                cb.equal(member.get("user").get("id"), actor.id()));
                        predicates.add(cb.or(cb.exists(membership),
                                cb.equal(root.get("requester").get("id"), actor.id())));
                    }
                }
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
