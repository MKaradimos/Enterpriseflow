package com.enterpriseflow.dashboard;

import com.enterpriseflow.approval.dto.ApprovalDtos.ApprovalResponse;
import com.enterpriseflow.audit.AuditLogResponse;
import com.enterpriseflow.project.ProjectStatus;
import com.enterpriseflow.task.TaskPriority;
import com.enterpriseflow.task.TaskStatus;
import com.enterpriseflow.task.dto.TaskDtos.TaskResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything the dashboard shows, already limited to the caller's projects. */
public record DashboardResponse(Kpis kpis,
                                Map<TaskStatus, Long> tasksByStatus,
                                Map<TaskPriority, Long> tasksByPriority,
                                List<ProjectProgress> projectProgress,
                                List<TaskResponse> overdueTasks,
                                List<ApprovalResponse> approvalsToReview,
                                List<AuditLogResponse> recentActivity) {

    public record Kpis(long totalProjects, long activeProjects, long totalTasks, long completedTasks,
                       long overdueTasks, long pendingApprovals) {
    }

    public record ProjectProgress(UUID id, String name, ProjectStatus status, long totalTasks, long doneTasks,
                                  int progressPercent) {
    }
}
