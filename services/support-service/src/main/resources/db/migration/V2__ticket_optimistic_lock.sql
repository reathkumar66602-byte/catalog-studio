-- Optimistic locking for concurrent ticket updates
ALTER TABLE support_tickets
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
