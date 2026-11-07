package com.enterpriseflow.user.dto;

import com.enterpriseflow.user.RoleName;
import com.enterpriseflow.user.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Public view of a user. Never includes the password hash or other internal fields. */
public record UserResponse(UUID id, String email, String fullName, String jobTitle, boolean active,
                           Set<RoleName> roles, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getJobTitle(),
                user.isActive(), user.roleNames(), user.getCreatedAt());
    }
}
