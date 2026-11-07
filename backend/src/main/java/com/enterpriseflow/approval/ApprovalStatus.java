package com.enterpriseflow.approval;

/** Lifecycle of an approval request: PENDING → APPROVED | REJECTED | CANCELLED. */
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
