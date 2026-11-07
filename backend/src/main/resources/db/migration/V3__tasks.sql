CREATE TABLE tasks (
    id                UUID         PRIMARY KEY,
    project_id        UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    status            VARCHAR(20)  NOT NULL,
    priority          VARCHAR(20)  NOT NULL,
    assignee_id       UUID         REFERENCES users (id),
    reporter_id       UUID         NOT NULL REFERENCES users (id),
    due_date          DATE,
    requires_approval BOOLEAN      NOT NULL DEFAULT FALSE,
    archived          BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at      TIMESTAMPTZ,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE', 'BLOCKED')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX idx_tasks_project_status ON tasks (project_id, status);
CREATE INDEX idx_tasks_assignee ON tasks (assignee_id);
-- Partial index for the "overdue tasks" dashboard query: only open, non-archived tasks with a deadline.
CREATE INDEX idx_tasks_open_due_date ON tasks (due_date)
    WHERE archived = FALSE AND status <> 'DONE' AND due_date IS NOT NULL;
