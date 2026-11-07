CREATE TABLE refresh_tokens (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SHA-256 of the token, hex-encoded. The raw token exists only in the client's HttpOnly cookie.
    token_hash     VARCHAR(64) NOT NULL,
    -- All tokens produced by rotating one login share a family; reuse of an old token revokes the family.
    family_id      UUID        NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    replaced_by_id UUID        REFERENCES refresh_tokens (id),
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
