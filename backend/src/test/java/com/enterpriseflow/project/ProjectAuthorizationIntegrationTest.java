package com.enterpriseflow.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterpriseflow.IntegrationTestSupport;
import com.enterpriseflow.project.dto.ProjectDtos.CreateProjectRequest;
import com.enterpriseflow.task.TaskPriority;
import com.enterpriseflow.task.TaskService;
import com.enterpriseflow.task.dto.TaskDtos.CreateTaskRequest;
import com.enterpriseflow.user.RoleName;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Server-side RBAC and project-level authorization, exercised through the real HTTP layer. */
class ProjectAuthorizationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TaskService taskService;

    private TestUser manager;
    private TestUser member;
    private TestUser outsider;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        manager = createUser(RoleName.MANAGER);
        member = createUser(RoleName.EMPLOYEE);
        outsider = createUser(RoleName.EMPLOYEE);
        projectId = projectService.create(manager.principal(),
                new CreateProjectRequest("Project " + UUID.randomUUID(), null, null, null, null, null)).id();
        projectService.addMember(projectId, manager.principal(), member.id());
    }

    @Test
    void employeesCannotCreateProjects() throws Exception {
        mockMvc.perform(post("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Nope"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void membersCanViewButNotModifyTheProject() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + projectId).header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(false));

        mockMvc.perform(post("/api/v1/projects/" + projectId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonMembersGet404SoProjectIdsCannotBeProbed() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + projectId).header(HttpHeaders.AUTHORIZATION, outsider.bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projects/" + projectId + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, outsider.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void projectListIsScopedToMemberships() throws Exception {
        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, outsider.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/projects").header(HttpHeaders.AUTHORIZATION, member.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(projectId.toString()));
    }

    @Test
    void onlyAdminsCanAccessUserManagement() throws Exception {
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, manager.bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users").header(HttpHeaders.AUTHORIZATION, createUser(RoleName.ADMIN).bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void tasksCanOnlyBeAssignedToProjectMembers() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/tasks")
                        .header(HttpHeaders.AUTHORIZATION, manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Task", "priority", "HIGH", "assigneeId", outsider.id()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAssigneeOrManagerCanChangeTaskStatus() throws Exception {
        TestUser otherMember = createUser(RoleName.EMPLOYEE);
        projectService.addMember(projectId, manager.principal(), otherMember.id());
        UUID taskId = taskService.create(projectId, manager.principal(),
                new CreateTaskRequest("Work", null, TaskPriority.MEDIUM, member.id(), null, false)).id();

        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, otherMember.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void invalidStatusTransitionIsAConflict() throws Exception {
        UUID taskId = taskService.create(projectId, manager.principal(),
                new CreateTaskRequest("Jump", null, TaskPriority.LOW, member.id(), null, false)).id();

        mockMvc.perform(patch("/api/v1/tasks/" + taskId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, member.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "DONE"))))
                .andExpect(status().isConflict());
    }

    @Test
    void archivedProjectsAreReadOnly() throws Exception {
        projectService.archive(projectId, manager.principal());

        mockMvc.perform(put("/api/v1/projects/" + projectId).header(HttpHeaders.AUTHORIZATION, manager.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Renamed", "status", "ACTIVE"))))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownSortPropertyIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/projects?sort=createdBy").header(HttpHeaders.AUTHORIZATION, manager.bearer()))
                .andExpect(status().isBadRequest());
    }
}
