package com.enterpriseflow.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterpriseflow.IntegrationTestSupport;
import com.enterpriseflow.approval.dto.ApprovalDtos.ApprovalResponse;
import com.enterpriseflow.approval.dto.ApprovalDtos.SubmitApprovalRequest;
import com.enterpriseflow.common.error.BadRequestException;
import com.enterpriseflow.common.error.ConflictException;
import com.enterpriseflow.common.error.ResourceNotFoundException;
import com.enterpriseflow.notification.NotificationService;
import com.enterpriseflow.project.ProjectService;
import com.enterpriseflow.project.dto.ProjectDtos.CreateProjectRequest;
import com.enterpriseflow.task.TaskPriority;
import com.enterpriseflow.task.TaskService;
import com.enterpriseflow.task.TaskStatus;
import com.enterpriseflow.task.dto.TaskDtos.CreateTaskRequest;
import com.enterpriseflow.user.RoleName;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

/**
 * End-to-end approval rules against a real database: who may decide, one decision per request, the effects of
 * approving (task completed, budget changed, requester notified) and correctness under concurrent decisions.
 */
class ApprovalWorkflowIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private NotificationService notificationService;

    private TestUser admin;
    private TestUser manager;
    private TestUser employee;
    private UUID projectId;
    private UUID taskId;

    @BeforeEach
    void setUp() {
        admin = createUser(RoleName.ADMIN);
        manager = createUser(RoleName.MANAGER);
        employee = createUser(RoleName.EMPLOYEE);
        projectId = projectService.create(manager.principal(), new CreateProjectRequest(
                "Approvals " + UUID.randomUUID(), null, null, new BigDecimal("1000.00"), null, null)).id();
        projectService.addMember(projectId, manager.principal(), employee.id());

        taskId = taskService.create(projectId, manager.principal(),
                new CreateTaskRequest("Needs sign-off", null, TaskPriority.HIGH, employee.id(), null, true)).id();
        taskService.changeStatus(taskId, employee.principal(), TaskStatus.IN_PROGRESS);
        taskService.changeStatus(taskId, employee.principal(), TaskStatus.IN_REVIEW);
    }

    @Test
    void taskRequiringApprovalCannotBeCompletedDirectly() {
        assertThatThrownBy(() -> taskService.changeStatus(taskId, manager.principal(), TaskStatus.DONE))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void approvingACompletionRequestCompletesTheTaskAndNotifiesTheRequester() {
        ApprovalResponse request = submitCompletion(employee);
        long unreadBefore = notificationService.unreadCount(employee.id());

        ApprovalResponse decided = approvalService.approve(request.id(), manager.principal(), "Looks good");

        assertThat(decided.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(decided.reviewer().id()).isEqualTo(manager.id());
        assertThat(decided.decidedAt()).isNotNull();
        assertThat(taskService.get(taskId, manager.principal()).status()).isEqualTo(TaskStatus.DONE);
        assertThat(notificationService.unreadCount(employee.id())).isEqualTo(unreadBefore + 1);
    }

    @Test
    void rejectionLeavesTheTaskInReviewAndRequiresAComment() {
        ApprovalResponse request = submitCompletion(employee);

        assertThatThrownBy(() -> approvalService.reject(request.id(), manager.principal(), " "))
                .isInstanceOf(BadRequestException.class);

        approvalService.reject(request.id(), manager.principal(), "Missing tests");
        assertThat(taskService.get(taskId, manager.principal()).status()).isEqualTo(TaskStatus.IN_REVIEW);
    }

    @Test
    void requesterCannotApproveTheirOwnRequest() {
        // The manager submits for their own project: allowed, but they may not also approve it.
        ApprovalResponse request = submitCompletion(manager);

        assertThatThrownBy(() -> approvalService.approve(request.id(), manager.principal(), null))
                .isInstanceOf(AccessDeniedException.class);
        // An admin can.
        assertThat(approvalService.approve(request.id(), admin.principal(), null).status())
                .isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void employeesCannotDecideRequests() {
        TestUser colleague = createUser(RoleName.EMPLOYEE);
        projectService.addMember(projectId, manager.principal(), colleague.id());
        ApprovalResponse request = submitCompletion(employee);

        assertThatThrownBy(() -> approvalService.approve(request.id(), colleague.principal(), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aDecidedRequestCannotBeDecidedAgain() {
        ApprovalResponse request = submitCompletion(employee);
        approvalService.approve(request.id(), manager.principal(), null);

        assertThatThrownBy(() -> approvalService.reject(request.id(), admin.principal(), "Too late"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void onlyOnePendingRequestPerTask() {
        submitCompletion(employee);

        assertThatThrownBy(() -> submitCompletion(employee)).isInstanceOf(ConflictException.class);
    }

    @Test
    void budgetChangesNeedAnAdminAndUpdateTheProjectOnApproval() {
        ApprovalResponse request = approvalService.submit(manager.principal(), new SubmitApprovalRequest(
                ApprovalType.PROJECT_BUDGET_CHANGE, projectId, null, new BigDecimal("2500.00"), "More scope"));

        // Budget changes are reviewed by admins only; a manager of another project can't even see the request.
        TestUser otherManager = createUser(RoleName.MANAGER);
        assertThatThrownBy(() -> approvalService.approve(request.id(), otherManager.principal(), null))
                .isInstanceOf(ResourceNotFoundException.class);

        approvalService.approve(request.id(), admin.principal(), "Approved by finance");
        assertThat(projectService.get(projectId, admin.principal()).budget()).isEqualByComparingTo("2500.00");
    }

    /**
     * Two reviewers approve at the same instant. Optimistic locking (@Version) must let exactly one win;
     * the other fails with a conflict instead of double-applying the decision.
     */
    @Test
    void concurrentDecisionsProduceExactlyOneWinner() throws Exception {
        ApprovalResponse request = submitCompletion(employee);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        List<Callable<ApprovalResponse>> reviewers = List.of(
                () -> {
                    start.await();
                    return approvalService.approve(request.id(), manager.principal(), "manager");
                },
                () -> {
                    start.await();
                    return approvalService.approve(request.id(), admin.principal(), "admin");
                });

        List<Future<ApprovalResponse>> futures = new ArrayList<>();
        for (Callable<ApprovalResponse> reviewer : reviewers) {
            futures.add(pool.submit(reviewer));
        }
        start.countDown();

        int successes = 0;
        int conflicts = 0;
        for (Future<ApprovalResponse> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
                successes++;
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                // Either the loser read the request after the winner committed (ConflictException), or both read
                // it as PENDING and the loser's version check failed at commit.
                assertThat(cause).isInstanceOfAny(ConflictException.class,
                        ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class);
                conflicts++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);
        assertThat(approvalService.get(request.id(), admin.principal()).status()).isEqualTo(ApprovalStatus.APPROVED);
    }

    private ApprovalResponse submitCompletion(TestUser requester) {
        return approvalService.submit(requester.principal(), new SubmitApprovalRequest(
                ApprovalType.TASK_COMPLETION, null, taskId, null, "Ready for sign-off"));
    }
}
