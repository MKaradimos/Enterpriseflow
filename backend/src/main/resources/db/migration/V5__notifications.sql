CREATE TABLE notifications (
    id             UUID         PRIMARY KEY,
    recipient_id   UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type           VARCHAR(40)  NOT NULL,
    title          VARCHAR(200) NOT NULL,
    message        VARCHAR(500) NOT NULL,
    reference_type VARCHAR(40),
    reference_id   UUID,
    read_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notifications_recipient_created ON notifications (recipient_id, created_at DESC);
-- Partial index makes the unread badge count cheap regardless of history size.
CREATE INDEX idx_notifications_unread ON notifications (recipient_id) WHERE read_at IS NULL;
