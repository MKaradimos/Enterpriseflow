package com.enterpriseflow.security;

import com.enterpriseflow.user.RoleName;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller, built from the validated access token. Services receive it as a plain
 * parameter, which keeps them independent of Spring Security and easy to unit-test.
 */
public record CurrentUser(UUID id, String email, Set<RoleName> roles) {

    public boolean isAdmin() {
        return roles.contains(RoleName.ADMIN);
    }

    public boolean canManageProjects() {
        return isAdmin() || roles.contains(RoleName.MANAGER);
    }
}
