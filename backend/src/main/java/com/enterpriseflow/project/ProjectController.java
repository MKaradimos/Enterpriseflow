package com.enterpriseflow.project;

import com.enterpriseflow.audit.AuditEntityType;
import com.enterpriseflow.audit.AuditLogResponse;
import com.enterpriseflow.audit.AuditQueryService;
import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.common.api.Pageables;
import com.enterpriseflow.project.dto.ProjectDtos.AddMemberRequest;
import com.enterpriseflow.project.dto.ProjectDtos.AssignManagerRequest;
import com.enterpriseflow.project.dto.ProjectDtos.CreateProjectRequest;
import com.enterpriseflow.project.dto.ProjectDtos.ProjectMemberResponse;
import com.enterpriseflow.project.dto.ProjectDtos.ProjectResponse;
import com.enterpriseflow.project.dto.ProjectDtos.UpdateProjectRequest;
import com.enterpriseflow.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP layer: binds and validates input, delegates to the service, shapes the HTTP response. */
@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects")
public class ProjectController {

    private static final Set<String> SORTABLE = Set.of("name", "status", "dueDate", "createdAt", "updatedAt");

    private final ProjectService projectService;
    private final ProjectAccessService access;
    private final AuditQueryService auditQueryService;

    public ProjectController(ProjectService projectService, ProjectAccessService access,
                             AuditQueryService auditQueryService) {
        this.projectService = projectService;
        this.access = access;
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @Operation(summary = "List projects visible to the caller (all for ADMIN, memberships otherwise)")
    public PageResponse<ProjectResponse> list(CurrentUser user,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required = false) ProjectStatus status,
                                              @RequestParam(defaultValue = "false") boolean includeArchived,
                                              @PageableDefault(size = 20, sort = "updatedAt",
                                                      direction = Sort.Direction.DESC) Pageable pageable) {
        return projectService.search(user, q, status, includeArchived, Pageables.restrictSort(pageable, SORTABLE));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Project details with task progress")
    public ProjectResponse get(CurrentUser user, @PathVariable UUID projectId) {
        return projectService.get(projectId, user);
    }

    @PostMapping
    @Operation(summary = "Create a project (MANAGER or ADMIN)")
    public ResponseEntity<ProjectResponse> create(CurrentUser user, @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse created = projectService.create(user, request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + created.id())).body(created);
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "Update project details and status (project manager or ADMIN)")
    public ProjectResponse update(CurrentUser user, @PathVariable UUID projectId,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return projectService.update(projectId, user, request);
    }

    @PutMapping("/{projectId}/manager")
    @Operation(summary = "Assign a different project manager (ADMIN)")
    public ProjectResponse assignManager(CurrentUser user, @PathVariable UUID projectId,
                                         @Valid @RequestBody AssignManagerRequest request) {
        return projectService.assignManager(projectId, user, request.managerId());
    }

    @PostMapping("/{projectId}/archive")
    @Operation(summary = "Archive a project, making it read-only (project manager or ADMIN)")
    public ProjectResponse archive(CurrentUser user, @PathVariable UUID projectId) {
        return projectService.archive(projectId, user);
    }

    @PostMapping("/{projectId}/restore")
    @Operation(summary = "Restore an archived project to ON_HOLD (ADMIN)")
    public ProjectResponse restore(CurrentUser user, @PathVariable UUID projectId) {
        return projectService.restore(projectId, user);
    }

    @GetMapping("/{projectId}/activity")
    @Operation(summary = "Audit trail of the project itself (members, status, archive)")
    public List<AuditLogResponse> activity(CurrentUser user, @PathVariable UUID projectId) {
        access.requireViewable(projectId, user);
        return auditQueryService.forEntity(AuditEntityType.PROJECT, projectId);
    }

    // ---- Members ----------------------------------------------------------------------------------------

    @GetMapping("/{projectId}/members")
    @Operation(summary = "List project members")
    public List<ProjectMemberResponse> members(CurrentUser user, @PathVariable UUID projectId) {
        return projectService.members(projectId, user);
    }

    @PostMapping("/{projectId}/members")
    @Operation(summary = "Add a member (project manager or ADMIN)")
    public ResponseEntity<ProjectMemberResponse> addMember(CurrentUser user, @PathVariable UUID projectId,
                                                           @Valid @RequestBody AddMemberRequest request) {
        ProjectMemberResponse member = projectService.addMember(projectId, user, request.userId());
        return ResponseEntity.created(URI.create("/api/v1/projects/%s/members/%s".formatted(projectId, member.userId())))
                .body(member);
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @Operation(summary = "Remove a member (project manager or ADMIN)")
    public ResponseEntity<Void> removeMember(CurrentUser user, @PathVariable UUID projectId, @PathVariable UUID userId) {
        projectService.removeMember(projectId, user, userId);
        return ResponseEntity.noContent().build();
    }
}
