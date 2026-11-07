package com.enterpriseflow.user.dto;

import com.enterpriseflow.user.User;
import java.util.UUID;

/** Minimal reference to a user, embedded in other resources (task assignee, project manager, ...). */
public record UserSummary(UUID id, String fullName, String email) {

    public static UserSummary from(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getFullName(), user.getEmail());
    }
}
