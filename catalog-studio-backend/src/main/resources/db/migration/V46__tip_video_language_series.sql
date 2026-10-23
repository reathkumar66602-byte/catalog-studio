-- Multi-language tip videos (EN / HI / BN) grouped by series_key for AI-dubbed variants.
ALTER TABLE tip_videos
    ADD COLUMN IF NOT EXISTS language VARCHAR(8) NOT NULL DEFAULT 'EN',
    ADD COLUMN IF NOT EXISTS series_key UUID;

UPDATE tip_videos SET series_key = uuid WHERE series_key IS NULL;

ALTER TABLE tip_videos
    ALTER COLUMN series_key SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tip_videos_series_key ON tip_videos (series_key);
CREATE INDEX IF NOT EXISTS idx_tip_videos_kind_language ON tip_videos (kind, language);
