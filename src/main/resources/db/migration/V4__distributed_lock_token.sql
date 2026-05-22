-- Add token_id to distributed_locks for proper ownership validation
ALTER TABLE distributed_locks ADD COLUMN IF NOT EXISTS token_id VARCHAR(64);

-- Create index for token lookups
CREATE INDEX IF NOT EXISTS idx_distributed_locks_token ON distributed_locks(token_id);
