package com.enterpriseflow.user;

/** ADMIN runs everything, MANAGER runs projects, EMPLOYEE works on tasks. */
public enum RoleName {
    ADMIN,
    MANAGER,
    EMPLOYEE;

    /** Spring Security authority form, used by hasRole('ADMIN') checks. */
    public String authority() {
        return "ROLE_" + name();
    }
}
