package com.enterpriseflow.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterpriseflow.TestEntities;
import com.enterpriseflow.common.error.ConflictException;
import com.enterpriseflow.project.Project;
import com.enterpriseflow.user.User;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private User manager;
    private Project project;

    @BeforeEach
    void setUp() {
        manager = TestEntities.user("Mona Manager");
        project = TestEntities.project(manager);
    }

    @Test
    void completingSetsCompletedAtAndReopeningClearsIt() {
        Task task = task(false);
        task.changeStatus(TaskStatus.IN_PROGRESS, NOW);
        task.changeStatus(TaskStatus.IN_REVIEW, NOW);
        task.changeStatus(TaskStatus.DONE, NOW);

        assertThat(task.getCompletedAt()).isEqualTo(NOW);

        task.changeStatus(TaskStatus.IN_PROGRESS, NOW);
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test
    void tasksRequiringApprovalCannotBeMarkedDoneDirectly() {
        Task task = task(true);
        task.changeStatus(TaskStatus.IN_PROGRESS, NOW);
        task.changeStatus(TaskStatus.IN_REVIEW, NOW);

        assertThatThrownBy(() -> task.changeStatus(TaskStatus.DONE, NOW)).isInstanceOf(ConflictException.class);

        task.completeByApproval(NOW);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void approvalCompletionRequiresReviewState() {
        Task task = task(true);

        assertThatThrownBy(() -> task.completeByApproval(NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void tasksOfArchivedProjectsAreReadOnly() {
        Task task = task(false);
        project.archive();

        assertThatThrownBy(() -> task.changeStatus(TaskStatus.IN_PROGRESS, NOW))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> task.update("New", null, TaskPriority.LOW, null, false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void archivedTasksCannotBeModified() {
        Task task = task(false);
        task.archive();

        assertThatThrownBy(() -> task.assign(manager)).isInstanceOf(ConflictException.class);
    }

    @Test
    void overdueMeansPastDueAndNotDone() {
        LocalDate today = LocalDate.of(2026, 11, 7);
        Task task = new Task(project, "Late", null, TaskPriority.HIGH, manager, today.minusDays(1), false);

        assertThat(task.isOverdue(today)).isTrue();
        assertThat(new Task(project, "Soon", null, TaskPriority.HIGH, manager, today, false).isOverdue(today))
                .isFalse();
    }

    private Task task(boolean requiresApproval) {
        return new Task(project, "Task", null, TaskPriority.MEDIUM, manager, null, requiresApproval);
    }
}
