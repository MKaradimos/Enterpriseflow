package com.enterpriseflow.dashboard;

import com.enterpriseflow.approval.ApprovalService;
import com.enterpriseflow.approval.dto.ApprovalDtos.Scope;
import com.enterpriseflow.audit.AuditQueryService;
import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.task.TaskFilter;
import com.enterpriseflow.task.TaskService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read model that composes other modules' queries. One read-only transaction gives every widget a consistent
 * snapshot of the same moment.
 */
@Service
public class DashboardService {

    private static final int LIST_SIZE = 5;

    private final DashboardQueries queries;
    private final TaskService taskService;
    private final ApprovalService approvalService;
    private final AuditQueryService auditQueryService;
    private final Clock clock;

    public DashboardService(DashboardQueries queries, TaskService taskService, ApprovalService approvalService,
                            AuditQueryService auditQueryService, Clock clock) {
        this.queries = queries;
        this.taskService = taskService;
        this.approvalService = approvalService;
        this.auditQueryService = auditQueryService;
        this.clock = clock;
    }

    /**
     * Builds every dashboard section for the caller. Each part reuses the scoped queries of its own module, so the
     * dashboard can never show data that the corresponding page would hide.
     */
    @Transactional(readOnly = true)
    public DashboardResponse build(CurrentUser user) {
        LocalDate today = LocalDate.now(clock);
        TaskFilter overdue = new TaskFilter(null, null, null, null, false, null, true, null, false);

        return new DashboardResponse(
                queries.kpis(user, today),
                queries.tasksByStatus(user),
                queries.openTasksByPriority(user),
                queries.projectProgress(user, 6),
                taskService.search(user, overdue, PageRequest.of(0, LIST_SIZE, Sort.by("dueDate"))).content(),
                approvalService.list(user, Scope.TO_REVIEW, null,
                        PageRequest.of(0, LIST_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))).content(),
                auditQueryService.recentActivity(user, 10));
    }
}
