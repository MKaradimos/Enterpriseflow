package com.enterpriseflow.task;

import com.enterpriseflow.audit.AuditAction;
import com.enterpriseflow.audit.AuditEntityType;
import com.enterpriseflow.audit.AuditService;
import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.common.error.BadRequestException;
import com.enterpriseflow.common.error.ResourceNotFoundException;
import com.enterpriseflow.notification.NotificationService;
import com.enterpriseflow.notification.NotificationType;
import com.enterpriseflow.project.Project;
import com.enterpriseflow.project.ProjectAccessService;
import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.task.dto.TaskDtos.CreateTaskRequest;
import com.enterpriseflow.task.dto.TaskDtos.ProjectRef;
import com.enterpriseflow.task.dto.TaskDtos.TaskResponse;
import com.enterpriseflow.task.dto.TaskDtos.UpdateTaskRequest;
import com.enterpriseflow.user.User;
import com.enterpriseflow.user.UserService;
import com.enterpriseflow.user.dto.UserSummary;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task rules:
 * <ul>
 *   <li>create, edit, assign, archive: the project manager or an ADMIN</li>
 *   <li>change status: the above, or the task's assignee</li>
 *   <li>reopen a DONE task: the project manager or an ADMIN only</li>
 *   <li>assignees must be active members of the task's project</li>
 * </ul>
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectAccessService access;
    private final UserService userService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository, ProjectAccessService access, UserService userService,
                       AuditService auditService, NotificationService notificationService, Clock clock) {
        this.taskRepository = taskRepository;
        this.access = access;
        this.userService = userService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    // ---- Queries ----------------------------------------------------------------------------------------

    /**
     * Task list with filters. When a project filter is given, access to that project is checked first, so a
     * foreign project id returns 404, consistent with every other project endpoint.
     */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> search(CurrentUser user, TaskFilter filter, Pageable pageable) {
        if (filter.projectId() != null) {
            access.requireViewable(filter.projectId(), user);
        }
        LocalDate today = LocalDate.now(clock);
        return PageResponse.from(
                taskRepository.findAll(TaskSpecifications.matching(filter, user, today), pageable),
                task -> toResponse(task, user));
    }

    /** One task, including which actions the caller may perform on it. */
    @Transactional(readOnly = true)
    public TaskResponse get(UUID taskId, CurrentUser user) {
        return toResponse(requireViewable(taskId, user), user);
    }

    /** Loads a task and checks the caller may see it; inaccessible tasks are reported as not found. */
    @Transactional(readOnly = true)
    public Task requireViewable(UUID taskId, CurrentUser user) {
        Task task = taskRepository.findDetailedById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        if (!access.canView(task.getProject(), user)) {
            throw new ResourceNotFoundException("Task", taskId);
        }
        return task;
    }

    // ---- Commands ---------------------------------------------------------------------------------------

    /** Creates a task in a project the caller manages, optionally assigned, and notifies the assignee. */
    @Transactional
    public TaskResponse create(UUID projectId, CurrentUser actor, CreateTaskRequest request) {
        Project project = access.requireManageable(projectId, actor);
        User reporter = userService.findWithRoles(actor.id());

        Task task = new Task(project, request.title(), request.description(), request.priority(), reporter,
                request.dueDate(), request.requiresApproval());
        User assignee = resolveAssignee(project, request.assigneeId());
        task.assign(assignee);
        // Flush so database-generated values (timestamps) are populated in the response.
        taskRepository.saveAndFlush(task);

        auditService.record(actor.id(), AuditAction.TASK_CREATED, AuditEntityType.TASK, task.getId(), projectId,
                Map.of("title", task.getTitle(), "priority", task.getPriority()));
        notifyAssignment(task, assignee, actor);
        return toResponse(task, actor);
    }

    /**
     * Updates the task. A change of assignee gets its own audit entry and a notification, because "who is
     * responsible" is the change people most often need to trace.
     */
    @Transactional
    public TaskResponse update(UUID taskId, CurrentUser actor, UpdateTaskRequest request) {
        Task task = requireEditable(taskId, actor);
        UUID previousAssignee = task.getAssignee() == null ? null : task.getAssignee().getId();

        task.update(request.title(), request.description(), request.priority(), request.dueDate(),
                request.requiresApproval());
        User assignee = resolveAssignee(task.getProject(), request.assigneeId());
        task.assign(assignee);

        auditService.record(actor.id(), AuditAction.TASK_UPDATED, AuditEntityType.TASK, taskId,
                task.getProject().getId(), Map.of("title", task.getTitle(), "priority", task.getPriority()));

        UUID newAssignee = assignee == null ? null : assignee.getId();
        if (!Objects.equals(previousAssignee, newAssignee)) {
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("from", previousAssignee);
            change.put("to", newAssignee);
            auditService.record(actor.id(), AuditAction.TASK_ASSIGNED, AuditEntityType.TASK, taskId,
                    task.getProject().getId(), change);
            notifyAssignment(task, assignee, actor);
        }
        return toResponse(task, actor);
    }

    /**
     * Moves the task through its workflow. The assignee may progress their own work, but reopening a DONE task is
     * reserved for the project manager, so completed work cannot be silently undone. Invalid transitions are
     * rejected by the entity.
     */
    @Transactional
    public TaskResponse changeStatus(UUID taskId, CurrentUser actor, TaskStatus target) {
        Task task = requireViewable(taskId, actor);
        boolean canManage = access.canManage(task.getProject(), actor);

        if (!canManage && !task.isAssignedTo(actor.id())) {
            throw new AccessDeniedException("Only the assignee or the project manager can change this task's status.");
        }
        if (task.getStatus() == TaskStatus.DONE && !canManage) {
            throw new AccessDeniedException("Only the project manager can reopen a completed task.");
        }

        TaskStatus before = task.getStatus();
        task.changeStatus(target, clock.instant());
        if (before != target) {
            auditService.record(actor.id(), AuditAction.TASK_STATUS_CHANGED, AuditEntityType.TASK, taskId,
                    task.getProject().getId(), Map.of("from", before, "to", target));
        }
        return toResponse(task, actor);
    }

    /** Soft-deletes a task; it keeps its history and approval records. */
    @Transactional
    public void archive(UUID taskId, CurrentUser actor) {
        Task task = requireEditable(taskId, actor);
        task.archive();
        auditService.record(actor.id(), AuditAction.TASK_ARCHIVED, AuditEntityType.TASK, taskId,
                task.getProject().getId());
    }

    /** Called by the approval workflow; authorization was checked when the reviewer decided. */
    @Transactional
    public void completeByApproval(Task task, UUID approverId) {
        TaskStatus before = task.getStatus();
        task.completeByApproval(clock.instant());
        auditService.record(approverId, AuditAction.TASK_STATUS_CHANGED, AuditEntityType.TASK, task.getId(),
                task.getProject().getId(), Map.of("from", before, "to", TaskStatus.DONE, "via", "approval"));
    }

    // ---- Helpers ----------------------------------------------------------------------------------------

    /** Loads a task whose project the caller manages; editing a task is a management action. */
    private Task requireEditable(UUID taskId, CurrentUser actor) {
        Task task = requireViewable(taskId, actor);
        if (!access.canManage(task.getProject(), actor)) {
            throw new AccessDeniedException("Only the project manager or an administrator can edit tasks.");
        }
        return task;
    }

    /**
     * Validates the assignee: must be active and a member of the project. Otherwise a task could be given to
     * someone who cannot even see it.
     */
    private User resolveAssignee(Project project, UUID assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        User assignee = userService.findWithRoles(assigneeId);
        if (!assignee.isActive()) {
            throw new BadRequestException("Tasks cannot be assigned to deactivated users.");
        }
        if (!access.isMember(project.getId(), assigneeId)) {
            throw new BadRequestException("The assignee must be a member of the project.");
        }
        return assignee;
    }

    /** Notifies the new assignee, unless they assigned the task to themselves. */
    private void notifyAssignment(Task task, User assignee, CurrentUser actor) {
        if (assignee != null && !assignee.getId().equals(actor.id())) {
            notificationService.notify(assignee.getId(), NotificationType.TASK_ASSIGNED, "New task assigned",
                    "You were assigned \"%s\" in %s.".formatted(task.getTitle(), task.getProject().getName()),
                    "TASK", task.getId());
        }
    }

    /**
     * Maps to the API response, including {@code canEdit} and the statuses the caller may move the task to.
     * DONE is left out for tasks that require approval, because those reach DONE only through an approved request.
     */
    TaskResponse toResponse(Task task, CurrentUser user) {
        Project project = task.getProject();
        boolean canManage = access.canManage(project, user);
        boolean editable = !task.isArchived() && !project.isArchived();
        boolean canChangeStatus = editable && (canManage || task.isAssignedTo(user.id()));

        Set<TaskStatus> allowed = EnumSet.noneOf(TaskStatus.class);
        if (canChangeStatus && (task.getStatus() != TaskStatus.DONE || canManage)) {
            allowed.addAll(task.getStatus().allowedNext());
            if (task.isRequiresApproval()) {
                allowed.remove(TaskStatus.DONE);
            }
        }

        return new TaskResponse(task.getId(), new ProjectRef(project.getId(), project.getName()), task.getTitle(),
                task.getDescription(), task.getStatus(), task.getPriority(), UserSummary.from(task.getAssignee()),
                UserSummary.from(task.getReporter()), task.getDueDate(), task.isOverdue(LocalDate.now(clock)),
                task.isRequiresApproval(), task.isArchived(), task.getCompletedAt(), task.getCreatedAt(),
                task.getUpdatedAt(), editable && canManage, allowed);
    }
}
