-- Split tip videos into free app guides vs paid ecommerce tutorials; add Q&A comments + tutorial plans.

ALTER TABLE tip_videos
    ADD COLUMN IF NOT EXISTS kind VARCHAR(32) NOT NULL DEFAULT 'ECOMMERCE_TUTORIAL';

ALTER TABLE tip_videos
    DROP CONSTRAINT IF EXISTS chk_tip_videos_kind;

ALTER TABLE tip_videos
    ADD CONSTRAINT chk_tip_videos_kind CHECK (kind IN ('APP_GUIDE', 'ECOMMERCE_TUTORIAL'));

UPDATE tip_videos SET kind = 'ECOMMERCE_TUTORIAL' WHERE kind IS NULL OR kind = '';

CREATE INDEX IF NOT EXISTS idx_tip_videos_kind_created ON tip_videos (kind, created_at DESC);

CREATE TABLE IF NOT EXISTS tip_video_comments (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    tip_video_id    BIGINT NOT NULL REFERENCES tip_videos (id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_id       BIGINT REFERENCES tip_video_comments (id) ON DELETE CASCADE,
    body            TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tip_video_comments_body CHECK (char_length(btrim(body)) BETWEEN 1 AND 4000)
);

CREATE INDEX IF NOT EXISTS idx_tip_video_comments_video ON tip_video_comments (tip_video_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tip_video_comments_parent ON tip_video_comments (parent_id);

-- Standalone ecommerce tutorial subscriptions (stackable with main Catalog Studio plans).
INSERT INTO subscription_plans (name, price, billing_cycle, features_json, status)
VALUES
    ('TUTORIAL_QUARTERLY', 499.00, 'QUARTERLY',
     '{"displayName":"Tutorials","group":"tutorial","termMonths":3,"ecommerceTutorials":"Ecommerce tutorial videos","ecommerceTips":"Ecommerce tutorial videos"}'::jsonb, 'ACTIVE'),
    ('TUTORIAL_HALF_YEARLY', 899.00, 'HALF_YEARLY',
     '{"displayName":"Tutorials","group":"tutorial","termMonths":6,"ecommerceTutorials":"Ecommerce tutorial videos","ecommerceTips":"Ecommerce tutorial videos"}'::jsonb, 'ACTIVE'),
    ('TUTORIAL_YEARLY', 1499.00, 'YEARLY',
     '{"displayName":"Tutorials","group":"tutorial","termMonths":12,"badge":"Best value","ecommerceTutorials":"Ecommerce tutorial videos","ecommerceTips":"Ecommerce tutorial videos"}'::jsonb, 'ACTIVE')
ON CONFLICT (name) DO UPDATE SET
    price = EXCLUDED.price,
    billing_cycle = EXCLUDED.billing_cycle,
    status = 'ACTIVE',
    features_json = COALESCE(subscription_plans.features_json, '{}'::jsonb) || EXCLUDED.features_json;
