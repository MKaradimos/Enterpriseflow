package com.enterpriseflow.task;

import com.enterpriseflow.common.domain.BaseEntity;
import com.enterpriseflow.common.error.ConflictException;
import com.enterpriseflow.project.Project;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A unit of work inside a project. Status changes follow the {@link TaskStatus} state machine; tasks marked
 * {@code requiresApproval} can only reach DONE through an approved TASK_COMPLETION request.
 * Tasks are never deleted, only archived, so their history stays intact.
 */
@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

    // No inverse @OneToMany on Project: a project may have thousands of tasks, and they are always
    // queried with filters and pagination, never loaded as a whole collection.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false, updatable = false)
    private User reporter;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "completed_at", columnDefinition = "timestamptz")
    private Instant completedAt;

    // Optimistic locking: two people editing the same task → the second save fails with 409 instead of
    // silently overwriting the first.
    @Version
    private long version;

    protected Task() {
        // Required by JPA
    }

    public Task(Project project, String title, String description, TaskPriority priority, User reporter,
                LocalDate dueDate, boolean requiresApproval) {
        this.project = project;
        this.reporter = reporter;
        update(title, description, priority, dueDate, requiresApproval);
    }

    /** Replaces the editable fields. Not allowed on archived tasks or tasks of archived projects. */
    public void update(String title, String description, TaskPriority priority, LocalDate dueDate,
                       boolean requiresApproval) {
        ensureEditable();
        this.title = title.trim();
        this.description = description;
        this.priority = priority;
        this.dueDate = dueDate;
        this.requiresApproval = requiresApproval;
    }

    /** Membership of the assignee is validated by the service, which has access to the repository. */
    public void assign(User newAssignee) {
        ensureEditable();
        this.assignee = newAssignee;
    }

    /**
     * Regular status change. Moving to DONE is refused when the task requires approval; that path goes
     * through {@link #completeByApproval(Instant)}.
     */
    public void changeStatus(TaskStatus target, Instant now) {
        ensureEditable();
        if (target == status) {
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("A task cannot move from %s to %s.".formatted(status, target));
        }
        if (target == TaskStatus.DONE && requiresApproval) {
            throw new ConflictException("This task requires approval. Submit a completion request instead.");
        }
        applyStatus(target, now);
    }

    /** Invoked by the approval workflow inside the same transaction that approves the request. */
    public void completeByApproval(Instant now) {
        ensureEditable();
        if (status != TaskStatus.IN_REVIEW) {
            throw new ConflictException("Only tasks in review can be completed by approval.");
        }
        applyStatus(TaskStatus.DONE, now);
    }

    /** Soft delete: the task is hidden from lists but kept for history and audit. */
    public void archive() {
        if (archived) {
            throw new ConflictException("The task is already archived.");
        }
        project.ensureNotArchived();
        this.archived = true;
    }

    /** Clears the assignee, e.g. when that person leaves the project. */
    public void unassign() {
        this.assignee = null;
    }

    /** Overdue = has a due date in the past and is not done yet. */
    public boolean isOverdue(LocalDate today) {
        return dueDate != null && status != TaskStatus.DONE && dueDate.isBefore(today);
    }

    public boolean isAssignedTo(UUID userId) {
        return assignee != null && assignee.getId().equals(userId);
    }

    /** Sets the status and keeps {@code completedAt} in sync with DONE. */
    private void applyStatus(TaskStatus target, Instant now) {
        this.status = target;
        this.completedAt = target == TaskStatus.DONE ? now : null;
    }

    /** Guards every mutation: archived tasks and tasks of archived projects are read-only. */
    private void ensureEditable() {
        if (archived) {
            throw new ConflictException("Archived tasks cannot be modified.");
        }
        if (project != null) {
            project.ensureNotArchived();
        }
    }

    public Project getProject() {
        return project;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public User getAssignee() {
        return assignee;
    }

    public User getReporter() {
        return reporter;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
