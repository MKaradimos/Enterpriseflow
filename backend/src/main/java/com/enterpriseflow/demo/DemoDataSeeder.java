package com.enterpriseflow.demo;

import com.enterpriseflow.approval.ApprovalService;
import com.enterpriseflow.approval.ApprovalType;
import com.enterpriseflow.approval.dto.ApprovalDtos.SubmitApprovalRequest;
import com.enterpriseflow.project.ProjectService;
import com.enterpriseflow.project.ProjectStatus;
import com.enterpriseflow.project.dto.ProjectDtos.CreateProjectRequest;
import com.enterpriseflow.project.dto.ProjectDtos.ProjectResponse;
import com.enterpriseflow.project.dto.ProjectDtos.UpdateProjectRequest;
import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.task.TaskPriority;
import com.enterpriseflow.task.TaskService;
import com.enterpriseflow.task.TaskStatus;
import com.enterpriseflow.task.dto.TaskDtos.CreateTaskRequest;
import com.enterpriseflow.user.RoleName;
import com.enterpriseflow.user.User;
import com.enterpriseflow.user.UserRepository;
import com.enterpriseflow.user.UserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds fictional users, projects, tasks and approvals for local demos (profile {@code demo}).
 *
 * <p>All data goes through the real services, so audit entries, notifications and business rules are exactly
 * what a user would produce. Refuses to run together with the {@code prod} profile.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    static final String DOMAIN = "@enterpriseflow.local";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final ApprovalService approvalService;
    private final Environment environment;
    private final Clock clock;
    private final String password;

    public DemoDataSeeder(UserService userService, UserRepository userRepository, ProjectService projectService,
                          TaskService taskService, ApprovalService approvalService, Environment environment,
                          Clock clock, @Value("${app.demo.password}") String password) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.projectService = projectService;
        this.taskService = taskService;
        this.approvalService = approvalService;
        this.environment = environment;
        this.clock = clock;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException("The demo profile must never be combined with prod.");
        }
        if (userRepository.existsByEmail("admin" + DOMAIN)) {
            log.info("Demo data already present, skipping seed");
            return;
        }

        CurrentUser admin = user("admin", "Anna Admin", "Head of Operations", RoleName.ADMIN);
        CurrentUser maria = user("maria.manager", "Maria Manager", "Delivery Manager", RoleName.MANAGER);
        CurrentUser nikos = user("nikos.manager", "Nikos Manager", "Engineering Manager", RoleName.MANAGER);
        CurrentUser eva = user("eva.employee", "Eva Employee", "Backend Developer", RoleName.EMPLOYEE);
        CurrentUser leo = user("leo.employee", "Leo Employee", "Frontend Developer", RoleName.EMPLOYEE);
        CurrentUser sofia = user("sofia.employee", "Sofia Employee", "QA Engineer", RoleName.EMPLOYEE);

        LocalDate today = LocalDate.now(clock);

        // Project 1: active, with a task waiting for completion approval and one overdue task.
        UUID portal = project(maria, "Customer Portal Redesign", "New self-service portal for enterprise clients.",
                "120000.00", today.minusWeeks(6), today.plusWeeks(8), ProjectStatus.ACTIVE, eva, leo, sofia);
        UUID loginTask = task(maria, portal, "Implement SSO login", TaskPriority.CRITICAL, eva, today.plusDays(5), true);
        move(eva, loginTask, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW);
        approvalService.submit(eva, new SubmitApprovalRequest(ApprovalType.TASK_COMPLETION, null, loginTask, null,
                "SSO is implemented and tested against the staging identity provider."));
        UUID dashboardTask = task(maria, portal, "Build account dashboard", TaskPriority.HIGH, leo,
                today.minusDays(3), false);
        move(leo, dashboardTask, TaskStatus.IN_PROGRESS);
        UUID testPlan = task(maria, portal, "Write regression test plan", TaskPriority.MEDIUM, sofia,
                today.plusDays(10), false);
        move(sofia, testPlan, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW, TaskStatus.DONE);
        task(maria, portal, "Accessibility audit", TaskPriority.MEDIUM, null, today.plusWeeks(3), false);
        approvalService.submit(maria, new SubmitApprovalRequest(ApprovalType.PROJECT_BUDGET_CHANGE, portal, null,
                new BigDecimal("145000.00"), "Additional scope: mobile-responsive layouts requested by the client."));

        // Project 2: active, mostly complete.
        UUID migration = project(nikos, "Data Platform Migration", "Move reporting workloads to the new platform.",
                "80000.00", today.minusWeeks(10), today.plusWeeks(2), ProjectStatus.ACTIVE, eva, sofia);
        UUID schema = task(nikos, migration, "Migrate reporting schema", TaskPriority.HIGH, eva, today.minusDays(7),
                false);
        move(eva, schema, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW, TaskStatus.DONE);
        UUID etl = task(nikos, migration, "Port nightly ETL jobs", TaskPriority.HIGH, eva, today.minusDays(1), false);
        move(eva, etl, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED);
        UUID validation = task(nikos, migration, "Validate migrated data", TaskPriority.CRITICAL, sofia,
                today.plusDays(4), false);
        move(sofia, validation, TaskStatus.IN_PROGRESS);

        // Project 3: planned, not started yet.
        UUID onboarding = project(maria, "Employee Onboarding Workflow", "Digital onboarding for new hires.",
                "30000.00", today.plusWeeks(2), today.plusWeeks(12), ProjectStatus.PLANNED, leo);
        task(maria, onboarding, "Define onboarding checklist", TaskPriority.LOW, leo, today.plusWeeks(3), false);

        log.info("Demo data seeded. Users: {} (password from app.demo.password)",
                List.of(admin.email(), maria.email(), nikos.email(), eva.email(), leo.email(), sofia.email()));
    }

    private CurrentUser user(String localPart, String name, String title, RoleName role) {
        User user = userService.createUser(localPart + DOMAIN, name, title, password, Set.of(role));
        return new CurrentUser(user.getId(), user.getEmail(), user.roleNames());
    }

    private UUID project(CurrentUser manager, String name, String description, String budget, LocalDate start,
                         LocalDate due, ProjectStatus status, CurrentUser... members) {
        ProjectResponse project = projectService.create(manager, new CreateProjectRequest(name, description, null,
                new BigDecimal(budget), start, due));
        for (CurrentUser member : members) {
            projectService.addMember(project.id(), manager, member.id());
        }
        if (status != ProjectStatus.PLANNED) {
            projectService.update(project.id(), manager, new UpdateProjectRequest(name, description, status, start, due));
        }
        return project.id();
    }

    private UUID task(CurrentUser manager, UUID projectId, String title, TaskPriority priority, CurrentUser assignee,
                      LocalDate due, boolean requiresApproval) {
        return taskService.create(projectId, manager, new CreateTaskRequest(title, null, priority,
                assignee == null ? null : assignee.id(), due, requiresApproval)).id();
    }

    private void move(CurrentUser actor, UUID taskId, TaskStatus... path) {
        for (TaskStatus status : path) {
            taskService.changeStatus(taskId, actor, status);
        }
    }
}
