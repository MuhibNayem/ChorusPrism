-- Migration to add the missing created_at column to the alert_events table
ALTER TABLE alert_events ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
