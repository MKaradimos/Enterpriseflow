package com.enterpriseflow.approval;

import com.enterpriseflow.common.domain.BaseEntity;
import com.enterpriseflow.common.error.ConflictException;
import com.enterpriseflow.project.Project;
import com.enterpriseflow.task.Task;
import com.enterpriseflow.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A request that needs a second person's decision: completing a task or changing a project budget.
 * Starts as PENDING and ends in exactly one final state (APPROVED, REJECTED or CANCELLED).
 * State changes go through the methods below, which enforce that a request is decided only once.
 */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private ApprovalType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", updatable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false, updatable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Column(nullable = false, length = 1000, updatable = false)
    private String reason;

    @Column(name = "requested_budget", precision = 14, scale = 2, updatable = false)
    private BigDecimal requestedBudget;

    @Column(name = "review_comment", length = 1000)
    private String reviewComment;

    @Column(name = "decided_at", columnDefinition = "timestamptz")
    private Instant decidedAt;

    /**
     * Two reviewers deciding the same request at the same moment both read version N; the first commit bumps
     * it to N+1 and the second fails its "WHERE version = N" update, so exactly one decision wins.
     */
    @Version
    private long version;

    protected ApprovalRequest() {
        // Required by JPA
    }

    private ApprovalRequest(ApprovalType type, Project project, Task task, User requester, String reason,
                            BigDecimal requestedBudget) {
        this.type = type;
        this.project = project;
        this.task = task;
        this.requester = requester;
        this.reason = reason.trim();
        this.requestedBudget = requestedBudget;
    }

    /** Factory for a TASK_COMPLETION request; the project is taken from the task. */
    public static ApprovalRequest taskCompletion(Task task, User requester, String reason) {
        return new ApprovalRequest(ApprovalType.TASK_COMPLETION, task.getProject(), task, requester, reason, null);
    }

    /** Factory for a PROJECT_BUDGET_CHANGE request carrying the proposed new budget. */
    public static ApprovalRequest budgetChange(Project project, BigDecimal budget, User requester, String reason) {
        return new ApprovalRequest(ApprovalType.PROJECT_BUDGET_CHANGE, project, null, requester, reason, budget);
    }

    void approve(User reviewer, String comment, Instant now) {
        decide(ApprovalStatus.APPROVED, reviewer, comment, now);
    }

    void reject(User reviewer, String comment, Instant now) {
        decide(ApprovalStatus.REJECTED, reviewer, comment, now);
    }

    /** Withdrawn by the requester before anyone decided it. */
    void cancel() {
        ensurePending();
        this.status = ApprovalStatus.CANCELLED;
    }

    private void decide(ApprovalStatus outcome, User reviewer, String comment, Instant now) {
        ensurePending();
        this.status = outcome;
        this.reviewer = reviewer;
        this.reviewComment = comment == null || comment.isBlank() ? null : comment.trim();
        this.decidedAt = now;
    }

    /** Prevents duplicate decisions: a decided request can never be decided again. */
    private void ensurePending() {
        if (status.isFinal()) {
            throw new ConflictException("This request is already %s.".formatted(status.name().toLowerCase()));
        }
    }

    public ApprovalType getType() {
        return type;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public Project getProject() {
        return project;
    }

    public Task getTask() {
        return task;
    }

    public User getRequester() {
        return requester;
    }

    public User getReviewer() {
        return reviewer;
    }

    public String getReason() {
        return reason;
    }

    public BigDecimal getRequestedBudget() {
        return requestedBudget;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
