package com.enterpriseflow.user;

import com.enterpriseflow.security.CurrentUser;
import com.enterpriseflow.user.dto.UserRequests.ChangePasswordRequest;
import com.enterpriseflow.user.dto.UserRequests.UpdateProfileRequest;
import com.enterpriseflow.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own account. The id always comes from the token, never from the request. */
@RestController
@RequestMapping("/api/v1/profile")
@Tag(name = "Profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Get the current user's profile")
    public UserResponse get(CurrentUser user) {
        return userService.get(user.id());
    }

    @PatchMapping
    @Operation(summary = "Update name and job title. Email and roles are managed by administrators.")
    public UserResponse update(CurrentUser user, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(user, request);
    }

    @PostMapping("/password")
    @Operation(summary = "Change password. Ends all sessions, including the current one.")
    public ResponseEntity<Void> changePassword(CurrentUser user, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user, request);
        return ResponseEntity.noContent().build();
    }
}
