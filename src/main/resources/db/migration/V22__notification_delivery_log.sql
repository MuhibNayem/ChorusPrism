CREATE TABLE IF NOT EXISTS notification_deliveries (
    delivery_id    TEXT PRIMARY KEY,
    event_id       TEXT NOT NULL,
    channel_id     TEXT NOT NULL,
    status         TEXT NOT NULL DEFAULT 'pending', -- pending, sent, failed, dlq
    attempt_count  INT  NOT NULL DEFAULT 0,
    last_error     TEXT,
    sent_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_notif_deliveries_event   ON notification_deliveries(event_id);
CREATE INDEX IF NOT EXISTS idx_notif_deliveries_channel ON notification_deliveries(channel_id);
CREATE INDEX IF NOT EXISTS idx_notif_deliveries_status  ON notification_deliveries(status);
