package com.enterpriseflow.task.dto;

import com.enterpriseflow.task.TaskPriority;
import com.enterpriseflow.task.TaskStatus;
import com.enterpriseflow.user.dto.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            @NotNull TaskPriority priority,
            UUID assigneeId,
            LocalDate dueDate,
            boolean requiresApproval) {
    }

    /** Full replacement of editable fields; a null assigneeId unassigns the task. */
    public record UpdateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String description,
            @NotNull TaskPriority priority,
            UUID assigneeId,
            LocalDate dueDate,
            boolean requiresApproval) {
    }

    public record ChangeStatusRequest(@NotNull TaskStatus status) {
    }

    public record ProjectRef(UUID id, String name) {
    }

    /**
     * {@code canEdit} and {@code allowedStatuses} are computed by the server for the caller, so the UI shows
     * only actions the backend will accept. The backend still re-checks every request.
     */
    public record TaskResponse(UUID id, ProjectRef project, String title, String description, TaskStatus status,
                               TaskPriority priority, UserSummary assignee, UserSummary reporter,
                               LocalDate dueDate, boolean overdue, boolean requiresApproval, boolean archived,
                               Instant completedAt, Instant createdAt, Instant updatedAt,
                               boolean canEdit, Set<TaskStatus> allowedStatuses) {
    }
}
