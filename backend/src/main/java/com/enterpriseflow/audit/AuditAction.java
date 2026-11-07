package com.enterpriseflow.audit;

/** Business actions recorded in the audit trail. Stored as text, so new values need no migration. */
public enum AuditAction {
    USER_REGISTERED,
    USER_CREATED,
    USER_ROLES_CHANGED,
    USER_ACTIVATED,
    USER_DEACTIVATED,
    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_ARCHIVED,
    PROJECT_RESTORED,
    PROJECT_MEMBER_ADDED,
    PROJECT_MEMBER_REMOVED,
    TASK_CREATED,
    TASK_UPDATED,
    TASK_ASSIGNED,
    TASK_STATUS_CHANGED,
    TASK_ARCHIVED,
    APPROVAL_SUBMITTED,
    APPROVAL_APPROVED,
    APPROVAL_REJECTED,
    APPROVAL_CANCELLED
}
