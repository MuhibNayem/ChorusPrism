-- Alert delivery retry tracking
ALTER TABLE alert_events ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE alert_events ADD COLUMN next_retry_at TIMESTAMPTZ;
ALTER TABLE alert_events ADD COLUMN last_error TEXT;

CREATE INDEX idx_alert_events_retry ON alert_events(next_retry_at) WHERE retry_count < 3;
