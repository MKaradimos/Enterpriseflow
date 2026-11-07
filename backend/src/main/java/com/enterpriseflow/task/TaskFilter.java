package com.enterpriseflow.task;

import java.time.LocalDate;
import java.util.UUID;

/** Optional task filters; null or false means "don't filter on this". */
public record TaskFilter(UUID projectId, TaskStatus status, TaskPriority priority, UUID assigneeId,
                         boolean assignedToMe, LocalDate dueBefore, boolean overdue, String q,
                         boolean includeArchived) {

    public TaskFilter withProject(UUID projectId) {
        return new TaskFilter(projectId, status, priority, assigneeId, assignedToMe, dueBefore, overdue, q,
                includeArchived);
    }
}
