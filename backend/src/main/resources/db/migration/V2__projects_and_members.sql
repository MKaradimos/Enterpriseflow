CREATE TABLE projects (
    id          UUID          PRIMARY KEY,
    name        VARCHAR(150)  NOT NULL,
    description TEXT,
    status      VARCHAR(20)   NOT NULL,
    manager_id  UUID          NOT NULL REFERENCES users (id),
    budget      NUMERIC(14, 2),
    start_date  DATE,
    due_date    DATE,
    created_by  UUID          NOT NULL REFERENCES users (id),
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_projects_status CHECK (status IN ('PLANNED', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT ck_projects_budget_positive CHECK (budget IS NULL OR budget >= 0),
    CONSTRAINT ck_projects_dates CHECK (start_date IS NULL OR due_date IS NULL OR due_date >= start_date)
);

CREATE INDEX idx_projects_status ON projects (status);
CREATE INDEX idx_projects_manager ON projects (manager_id);

CREATE TABLE project_members (
    id         UUID        PRIMARY KEY,
    -- Members disappear with their project; users are deactivated, never deleted, so no cascade there.
    project_id UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id),
    joined_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id)
);

-- The unique constraint already indexes (project_id, user_id); this one serves "projects of user X".
CREATE INDEX idx_project_members_user ON project_members (user_id);
