CREATE TABLE approval_requests (
    id               UUID           PRIMARY KEY,
    type             VARCHAR(40)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    project_id       UUID           NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    task_id          UUID           REFERENCES tasks (id) ON DELETE CASCADE,
    requester_id     UUID           NOT NULL REFERENCES users (id),
    reviewer_id      UUID           REFERENCES users (id),
    reason           VARCHAR(1000)  NOT NULL,
    requested_budget NUMERIC(14, 2),
    review_comment   VARCHAR(1000),
    decided_at       TIMESTAMPTZ,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_approvals_type CHECK (type IN ('TASK_COMPLETION', 'PROJECT_BUDGET_CHANGE')),
    CONSTRAINT ck_approvals_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    -- Each type carries exactly the data it needs.
    CONSTRAINT ck_approvals_payload CHECK (
        (type = 'TASK_COMPLETION' AND task_id IS NOT NULL AND requested_budget IS NULL) OR
        (type = 'PROJECT_BUDGET_CHANGE' AND task_id IS NULL AND requested_budget IS NOT NULL AND requested_budget >= 0)
    ),
    -- A decided request always records who decided and when.
    CONSTRAINT ck_approvals_decision CHECK (
        (status IN ('PENDING', 'CANCELLED')) OR (reviewer_id IS NOT NULL AND decided_at IS NOT NULL)
    )
);

-- At most one pending request per target. Enforced by the database, so it also holds under concurrency.
CREATE UNIQUE INDEX uk_approvals_one_pending_per_target
    ON approval_requests (type, project_id, COALESCE(task_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE status = 'PENDING';

CREATE INDEX idx_approvals_status ON approval_requests (status);
CREATE INDEX idx_approvals_project ON approval_requests (project_id);
CREATE INDEX idx_approvals_requester ON approval_requests (requester_id);
