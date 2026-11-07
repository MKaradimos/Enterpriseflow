package com.enterpriseflow.user;

import com.enterpriseflow.common.api.PageResponse;
import com.enterpriseflow.common.api.Pageables;
import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.user.dto.UserRequests.CreateUserRequest;
import com.enterpriseflow.user.dto.UserRequests.UpdateRolesRequest;
import com.enterpriseflow.user.dto.UserRequests.UpdateStatusRequest;
import com.enterpriseflow.user.dto.UserResponse;
import com.enterpriseflow.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration. Every endpoint is ADMIN-only except {@code /lookup}, which managers also need to pick
 * project members and task assignees.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

    private static final Set<String> SORTABLE = Set.of("fullName", "email", "createdAt");

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search users (ADMIN)")
    public PageResponse<UserResponse> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) RoleName role,
                                             @RequestParam(required = false) Boolean active,
                                             @PageableDefault(size = 20, sort = "fullName") Pageable pageable) {
        return userService.search(q, role, active, Pageables.restrictSort(pageable, SORTABLE));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Find active users by name or email, for member and assignee pickers")
    public List<UserSummary> lookup(@RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "10") int limit) {
        return userService.lookup(q, limit);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a user (ADMIN)")
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a user with roles and an initial password (ADMIN)")
    public ResponseEntity<UserResponse> create(CurrentUser actor, @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createByAdmin(actor, request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Replace a user's roles (ADMIN)")
    public UserResponse updateRoles(CurrentUser actor, @PathVariable UUID id,
                                    @Valid @RequestBody UpdateRolesRequest request) {
        return userService.updateRoles(actor, id, request.roles());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate or deactivate a user (ADMIN). Deactivation ends all sessions.")
    public UserResponse updateStatus(CurrentUser actor, @PathVariable UUID id,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        return userService.setActive(actor, id, request.active());
    }
}
