package com.enterpriseflow.approval;

public enum ApprovalType {
    /** Moves a task that requires approval from IN_REVIEW to DONE. Reviewed by the project manager or an ADMIN. */
    TASK_COMPLETION,
    /** Changes a project's budget. Reviewed by an ADMIN. */
    PROJECT_BUDGET_CHANGE
}
