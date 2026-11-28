ALTER TABLE campaign_runs
    ADD COLUMN IF NOT EXISTS pause_reason TEXT;
