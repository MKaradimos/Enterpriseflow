package com.enterpriseflow.project;

public enum ProjectStatus {
    PLANNED,
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    /** Read-only. Entered and left only through the dedicated archive/restore operations. */
    ARCHIVED
}
