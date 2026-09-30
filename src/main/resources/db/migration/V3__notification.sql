CREATE TABLE device_tokens (
    id TEXT PRIMARY KEY,
    owner TEXT NOT NULL,
    device_id TEXT NOT NULL,
    token TEXT NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_device_tokens_owner_device ON device_tokens (owner, device_id);

CREATE INDEX idx_device_tokens_active_by_owner ON device_tokens (owner) WHERE revoked_at IS NULL;

CREATE TABLE notification_outbox (
    id TEXT PRIMARY KEY,
    owner TEXT NOT NULL,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notification_outbox_claim ON notification_outbox (next_attempt_at, id) WHERE status = 'PENDING';
