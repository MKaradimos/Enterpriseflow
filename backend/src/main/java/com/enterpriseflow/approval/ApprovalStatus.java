package com.enterpriseflow.approval;

/** PENDING until someone approves, rejects or cancels it. */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    /** PENDING is the only state with outgoing transitions; every other state is final. */
    public boolean isFinal() {
        return this != PENDING;
    }
}
