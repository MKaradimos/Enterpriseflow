package com.enterpriseflow.user;

/**
 * Organization-wide roles. ADMIN manages users and everything else; MANAGER creates and runs projects;
 * EMPLOYEE works on tasks in the projects they belong to.
 */
public enum RoleName {
    ADMIN,
    MANAGER,
    EMPLOYEE;

    /** Spring Security authority form, used by hasRole('ADMIN') checks. */
    public String authority() {
        return "ROLE_" + name();
    }
}
