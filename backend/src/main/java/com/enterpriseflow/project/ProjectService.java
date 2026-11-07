package com.enterpriseflow.project;

import com.enterpriseflow.audit.AuditAction;
import com.enterpriseflow.audit.AuditEntityType;
import com.enterpriseflow.audit.AuditService;
import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.common.error.BadRequestException;
import com.enterpriseflow.common.error.ConflictException;
import com.enterpriseflow.common.persistence.SpecificationUtils;
import com.enterpriseflow.notification.NotificationService;
import com.enterpriseflow.notification.NotificationType;
import com.enterpriseflow.project.dto.ProjectDtos;
import com.enterpriseflow.project.dto.ProjectDtos.CreateProjectRequest;
import com.enterpriseflow.project.dto.ProjectDtos.ProjectMemberResponse;
import com.enterpriseflow.project.dto.ProjectDtos.ProjectResponse;
import com.enterpriseflow.project.dto.ProjectDtos.UpdateProjectRequest;
import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.user.User;
import com.enterpriseflow.user.UserService;
import com.enterpriseflow.user.dto.UserSummary;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Projects and their teams. Access check first, then the change, audit entry and notifications. */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectAccessService access;
    private final UserService userService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository,
                          ProjectAccessService access, UserService userService, AuditService auditService,
                          NotificationService notificationService, ApplicationEventPublisher events, Clock clock) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.access = access;
        this.userService = userService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.events = events;
        this.clock = clock;
    }

    // ---- Queries ----------------------------------------------------------------------------------------

    /**
     * Three queries regardless of page size: the page (with managers), task counts and member counts.
     * A naive implementation would issue 2 extra queries per project (the N+1 problem).
     */
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> search(CurrentUser user, String query, ProjectStatus status,
                                                boolean includeArchived, Pageable pageable) {
        Page<Project> page = projectRepository.findAll(searchSpec(user, query, status, includeArchived), pageable);
        List<UUID> ids = page.getContent().stream().map(Project::getId).toList();

        Map<UUID, ProjectRepository.TaskCounts> taskCounts = ids.isEmpty() ? Map.of()
                : projectRepository.countTasksByProject(ids).stream()
                        .collect(Collectors.toMap(ProjectRepository.TaskCounts::getProjectId, Function.identity()));
        Map<UUID, Long> memberCounts = ids.isEmpty() ? Map.of()
                : projectRepository.countMembersByProject(ids).stream()
                        .collect(Collectors.toMap(ProjectRepository.MemberCount::getProjectId,
                                ProjectRepository.MemberCount::getMemberCount));

        return PageResponse.from(page, p -> toResponse(p, user, taskCounts.get(p.getId()),
                memberCounts.getOrDefault(p.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID projectId, CurrentUser user) {
        return toResponse(access.requireViewable(projectId, user), user);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> members(UUID projectId, CurrentUser user) {
        Project project = access.requireViewable(projectId, user);
        UUID managerId = project.getManager().getId();
        return memberRepository.findByProjectIdOrderByJoinedAtAsc(projectId).stream()
                .map(m -> ProjectMemberResponse.from(m, managerId))
                .toList();
    }

    // ---- Commands ---------------------------------------------------------------------------------------

    @Transactional
    public ProjectResponse create(CurrentUser actor, CreateProjectRequest request) {
        if (!actor.canManageProjects()) {
            throw new AccessDeniedException("Only managers and administrators can create projects.");
        }
        // A manager creating a project leads it; only an admin may appoint someone else.
        UUID managerId = request.managerId() == null ? actor.id() : request.managerId();
        if (!managerId.equals(actor.id()) && !actor.isAdmin()) {
            throw new AccessDeniedException("Only administrators can assign another manager.");
        }
        User manager = requireEligibleManager(managerId);

        Project project = projectRepository.saveAndFlush(new Project(request.name(), request.description(), manager,
                request.budget(), request.startDate(), request.dueDate(), actor.id(), clock.instant()));

        auditService.record(actor.id(), AuditAction.PROJECT_CREATED, AuditEntityType.PROJECT, project.getId(),
                project.getId(), Map.of("name", project.getName(), "managerId", managerId));
        return toResponse(project, actor);
    }

    @Transactional
    public ProjectResponse update(UUID projectId, CurrentUser actor, UpdateProjectRequest request) {
        Project project = access.requireManageable(projectId, actor);
        ProjectStatus before = project.getStatus();

        project.update(request.name(), request.description(), request.startDate(), request.dueDate());
        project.changeStatus(request.status());

        auditService.record(actor.id(), AuditAction.PROJECT_UPDATED, AuditEntityType.PROJECT, projectId, projectId,
                Map.of("name", project.getName(), "statusFrom", before, "statusTo", project.getStatus()));
        return toResponse(project, actor);
    }

    @Transactional
    public ProjectResponse assignManager(UUID projectId, CurrentUser actor, UUID managerId) {
        if (!actor.isAdmin()) {
            throw new AccessDeniedException("Only administrators can change a project's manager.");
        }
        Project project = access.requireManageable(projectId, actor);
        User manager = requireEligibleManager(managerId);
        UUID previous = project.getManager().getId();
        project.assignManager(manager, clock.instant());

        auditService.record(actor.id(), AuditAction.PROJECT_UPDATED, AuditEntityType.PROJECT, projectId, projectId,
                Map.of("managerFrom", previous, "managerTo", managerId));
        return toResponse(project, actor);
    }

    @Transactional
    public ProjectResponse archive(UUID projectId, CurrentUser actor) {
        Project project = access.requireManageable(projectId, actor);
        project.archive();
        auditService.record(actor.id(), AuditAction.PROJECT_ARCHIVED, AuditEntityType.PROJECT, projectId, projectId);
        return toResponse(project, actor);
    }

    @Transactional
    public ProjectResponse restore(UUID projectId, CurrentUser actor) {
        if (!actor.isAdmin()) {
            throw new AccessDeniedException("Only administrators can restore archived projects.");
        }
        Project project = access.requireViewable(projectId, actor);
        project.restore();
        auditService.record(actor.id(), AuditAction.PROJECT_RESTORED, AuditEntityType.PROJECT, projectId, projectId);
        return toResponse(project, actor);
    }

    @Transactional
    public ProjectMemberResponse addMember(UUID projectId, CurrentUser actor, UUID userId) {
        Project project = access.requireManageable(projectId, actor);
        User user = userService.findWithRoles(userId);
        if (!user.isActive()) {
            throw new BadRequestException("Deactivated users cannot be added to projects.");
        }
        ProjectMember member = project.addMember(user, clock.instant())
                .orElseThrow(() -> new ConflictException("The user is already a member of this project."));
        // Flush so the membership row exists before the audit entry and response reference it.
        projectRepository.saveAndFlush(project);

        auditService.record(actor.id(), AuditAction.PROJECT_MEMBER_ADDED, AuditEntityType.PROJECT, projectId,
                projectId, Map.of("userId", userId));
        if (!userId.equals(actor.id())) {
            notificationService.notify(userId, NotificationType.PROJECT_MEMBERSHIP_ADDED,
                    "Added to project", "You were added to the project \"%s\".".formatted(project.getName()),
                    "PROJECT", projectId);
        }
        return ProjectMemberResponse.from(member, project.getManager().getId());
    }

    // The member's open tasks get unassigned via ProjectMemberRemovedEvent.
    @Transactional
    public void removeMember(UUID projectId, CurrentUser actor, UUID userId) {
        Project project = access.requireManageable(projectId, actor);
        project.removeMember(userId);
        // Flush the membership delete first: the listener's bulk update clears the persistence context.
        projectRepository.flush();
        events.publishEvent(new ProjectMemberRemovedEvent(projectId, userId));
        auditService.record(actor.id(), AuditAction.PROJECT_MEMBER_REMOVED, AuditEntityType.PROJECT, projectId,
                projectId, Map.of("userId", userId));
    }

    // ---- Helpers ----------------------------------------------------------------------------------------

    private User requireEligibleManager(UUID userId) {
        User user = userService.findWithRoles(userId);
        if (!user.isActive() || !user.canManageProjects()) {
            throw new BadRequestException("The project manager must be an active user with the MANAGER or ADMIN role.");
        }
        return user;
    }

    private ProjectResponse toResponse(Project project, CurrentUser user) {
        List<UUID> ids = List.of(project.getId());
        ProjectRepository.TaskCounts counts = projectRepository.countTasksByProject(ids).stream().findFirst().orElse(null);
        long members = projectRepository.countMembersByProject(ids).stream()
                .findFirst().map(ProjectRepository.MemberCount::getMemberCount).orElse(0L);
        return toResponse(project, user, counts, members);
    }

    private ProjectResponse toResponse(Project p, CurrentUser user, ProjectRepository.TaskCounts counts,
                                       long memberCount) {
        long total = counts == null ? 0 : counts.getTotalTasks();
        long done = counts == null ? 0 : counts.getDoneTasks();
        return new ProjectResponse(p.getId(), p.getName(), p.getDescription(), p.getStatus(),
                UserSummary.from(p.getManager()), p.getBudget(), p.getStartDate(), p.getDueDate(),
                total, done, ProjectDtos.progressPercent(total, done), memberCount,
                access.canManage(p, user), p.getCreatedAt(), p.getUpdatedAt());
    }

    private static Specification<Project> searchSpec(CurrentUser user, String query, ProjectStatus status,
                                                     boolean includeArchived) {
        return (root, criteria, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!user.isAdmin()) {
                // EXISTS subquery instead of a join, so a project never appears twice and counts stay correct.
                Subquery<UUID> membership = criteria.subquery(UUID.class);
                Root<ProjectMember> member = membership.from(ProjectMember.class);
                membership.select(member.get("id")).where(
                        cb.equal(member.get("project"), root),
                        cb.equal(member.get("user").get("id"), user.id()));
                predicates.add(cb.exists(membership));
            }
            if (query != null && !query.isBlank()) {
                predicates.add(SpecificationUtils.containsIgnoreCase(cb, root.get("name"), query));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else if (!includeArchived) {
                predicates.add(cb.notEqual(root.get("status"), ProjectStatus.ARCHIVED));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
