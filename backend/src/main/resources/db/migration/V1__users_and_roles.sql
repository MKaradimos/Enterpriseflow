-- Applied migrations are immutable: every schema change is a new V<n>__*.sql file.

CREATE TABLE roles (
    id   SMALLINT    PRIMARY KEY,
    name VARCHAR(20) NOT NULL UNIQUE
);

-- Fixed reference data: role ids are stable and referenced by code through the RoleName enum.
INSERT INTO roles (id, name) VALUES (1, 'ADMIN'), (2, 'MANAGER'), (3, 'EMPLOYEE');

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    -- Emails are normalized to lowercase by the application; the CHECK guarantees uniqueness is case-insensitive.
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    job_title     VARCHAR(120),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE user_roles (
    user_id UUID     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id SMALLINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role ON user_roles (role_id);
