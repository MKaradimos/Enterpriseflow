CREATE TABLE audit_logs (
    id          UUID        PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    -- Plain UUIDs without FKs: the audit trail must survive any change to the rows it describes.
    actor_id    UUID,
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id   UUID        NOT NULL,
    project_id  UUID,
    metadata    JSONB       NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_project ON audit_logs (project_id, occurred_at DESC);
CREATE INDEX idx_audit_occurred ON audit_logs (occurred_at DESC);

-- Append-only at the database level: even a bug or a compromised application account using plain
-- UPDATE/DELETE cannot rewrite history.
CREATE FUNCTION audit_logs_block_changes() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_logs is append-only';
END;
$$;

CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_block_changes();
