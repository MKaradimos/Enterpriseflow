package com.enterpriseflow.task;

import com.enterpriseflow.audit.AuditEntityType;
import com.enterpriseflow.audit.AuditLogResponse;
import com.enterpriseflow.audit.AuditQueryService;
import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.common.api.Pageables;
import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.task.dto.TaskDtos.ChangeStatusRequest;
import com.enterpriseflow.task.dto.TaskDtos.CreateTaskRequest;
import com.enterpriseflow.task.dto.TaskDtos.TaskResponse;
import com.enterpriseflow.task.dto.TaskDtos.UpdateTaskRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tasks across all visible projects (/tasks) or inside one project. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Tasks")
public class TaskController {

    private static final Set<String> SORTABLE = Set.of("title", "status", "priority", "dueDate", "createdAt",
            "updatedAt");

    private final TaskService taskService;
    private final AuditQueryService auditQueryService;

    public TaskController(TaskService taskService, AuditQueryService auditQueryService) {
        this.taskService = taskService;
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/tasks")
    @Operation(summary = "Search tasks across all projects visible to the caller")
    public PageResponse<TaskResponse> search(
            CurrentUser user,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueBefore,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        TaskFilter filter = new TaskFilter(projectId, status, priority, assigneeId, assignedToMe, dueBefore, overdue,
                q, includeArchived);
        return taskService.search(user, filter, Pageables.restrictSort(pageable, SORTABLE));
    }

    @GetMapping("/projects/{projectId}/tasks")
    @Operation(summary = "List the tasks of one project")
    public PageResponse<TaskResponse> projectTasks(
            CurrentUser user,
            @PathVariable UUID projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        TaskFilter filter = new TaskFilter(projectId, status, priority, assigneeId, false, null, false, q, false);
        return taskService.search(user, filter, Pageables.restrictSort(pageable, SORTABLE));
    }

    @PostMapping("/projects/{projectId}/tasks")
    @Operation(summary = "Create a task in a project (project manager or ADMIN)")
    public ResponseEntity<TaskResponse> create(CurrentUser user, @PathVariable UUID projectId,
                                               @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse created = taskService.create(projectId, user, request);
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + created.id())).body(created);
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Task details")
    public TaskResponse get(CurrentUser user, @PathVariable UUID taskId) {
        return taskService.get(taskId, user);
    }

    @PutMapping("/tasks/{taskId}")
    @Operation(summary = "Update a task, including its assignee (project manager or ADMIN)")
    public TaskResponse update(CurrentUser user, @PathVariable UUID taskId,
                               @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(taskId, user, request);
    }

    @PatchMapping("/tasks/{taskId}/status")
    @Operation(summary = "Move a task through its workflow (assignee, project manager or ADMIN)")
    public TaskResponse changeStatus(CurrentUser user, @PathVariable UUID taskId,
                                     @Valid @RequestBody ChangeStatusRequest request) {
        return taskService.changeStatus(taskId, user, request.status());
    }

    /** Soft delete: the task disappears from lists but keeps its history and approval records. */
    @DeleteMapping("/tasks/{taskId}")
    @Operation(summary = "Archive a task (project manager or ADMIN)")
    public ResponseEntity<Void> archive(CurrentUser user, @PathVariable UUID taskId) {
        taskService.archive(taskId, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tasks/{taskId}/activity")
    @Operation(summary = "Activity history of a task")
    public List<AuditLogResponse> activity(CurrentUser user, @PathVariable UUID taskId) {
        taskService.requireViewable(taskId, user);
        return auditQueryService.forEntity(AuditEntityType.TASK, taskId);
    }
}
