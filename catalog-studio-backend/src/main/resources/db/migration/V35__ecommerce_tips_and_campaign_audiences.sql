-- E-commerce Tips video library (FIFO caps) + campaign audiences for paid/trial active.
-- Local and prod stay in sync via Flyway (same V35 on both).

CREATE TABLE IF NOT EXISTS tip_video_settings (
    id               BIGSERIAL PRIMARY KEY,
    settings_key     VARCHAR(40) NOT NULL UNIQUE,
    max_count        INT NOT NULL DEFAULT 5,
    max_total_bytes  BIGINT NOT NULL DEFAULT 524288000,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tip_video_settings_max_count CHECK (max_count >= 1 AND max_count <= 50),
    CONSTRAINT chk_tip_video_settings_max_bytes CHECK (max_total_bytes >= 1048576 AND max_total_bytes <= 10737418240)
);

INSERT INTO tip_video_settings (settings_key, max_count, max_total_bytes)
VALUES ('default', 5, 524288000)
ON CONFLICT (settings_key) DO NOTHING;

CREATE TABLE IF NOT EXISTS tip_videos (
    id                   BIGSERIAL PRIMARY KEY,
    uuid                 UUID NOT NULL UNIQUE,
    title                VARCHAR(200) NOT NULL,
    storage_key          VARCHAR(255) NOT NULL,
    public_url           VARCHAR(500) NOT NULL,
    content_type         VARCHAR(100) NOT NULL,
    size_bytes           BIGINT NOT NULL,
    uploaded_by_user_id  BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tip_videos_size CHECK (size_bytes > 0)
);

CREATE INDEX IF NOT EXISTS idx_tip_videos_created ON tip_videos (created_at ASC);

ALTER TABLE campaign_runs
    ADD COLUMN IF NOT EXISTS tip_video_id BIGINT REFERENCES tip_videos (id) ON DELETE SET NULL;

ALTER TABLE notification_templates
    DROP CONSTRAINT IF EXISTS chk_notification_templates_type;
ALTER TABLE notification_templates
    ADD CONSTRAINT chk_notification_templates_type CHECK (
        campaign_type IN (
            'NO_PURCHASE',
            'TRIAL_EXPIRED',
            'EXPIRING_SOON',
            'PAID_ACTIVE',
            'TRIAL_ACTIVE'
        )
    );

ALTER TABLE campaign_runs
    DROP CONSTRAINT IF EXISTS chk_campaign_runs_type;
ALTER TABLE campaign_runs
    ADD CONSTRAINT chk_campaign_runs_type CHECK (
        campaign_type IN (
            'NO_PURCHASE',
            'TRIAL_EXPIRED',
            'EXPIRING_SOON',
            'PAID_ACTIVE',
            'TRIAL_ACTIVE'
        )
    );

INSERT INTO notification_templates (uuid, slug, name, channel, campaign_type, subject, body_text, body_html, variables_json, enabled)
VALUES
(
    gen_random_uuid(),
    'campaign-paid-active-email',
    'Tip video for paid subscribers',
    'EMAIL',
    'PAID_ACTIVE',
    'New E-commerce Tip video for you',
    'Hi {{name}}, a new seller tip video is ready: {{tipVideoTitle}}. Watch it here: {{tipVideoLink}} {{promoLine}}',
    '<p>Hi {{name}},</p><p>A new seller tip video is ready: <strong>{{tipVideoTitle}}</strong>.</p><p><a href="{{tipVideoLink}}">Watch E-commerce Tips</a></p><p>{{promoLine}}</p>',
    '["name","email","promoCode","promoLine","loginLink","tipVideoTitle","tipVideoLink","appName"]'::jsonb,
    TRUE
),
(
    gen_random_uuid(),
    'campaign-paid-active-whatsapp',
    'WhatsApp tip for paid subscribers',
    'WHATSAPP',
    'PAID_ACTIVE',
    NULL,
    'Hi {{name}}, new tip video: {{tipVideoTitle}}. Watch: {{tipVideoLink}} {{promoLine}}',
    NULL,
    '["name","email","promoCode","promoLine","loginLink","tipVideoTitle","tipVideoLink","appName"]'::jsonb,
    TRUE
),
(
    gen_random_uuid(),
    'campaign-trial-active-email',
    'Tip video for trial users',
    'EMAIL',
    'TRIAL_ACTIVE',
    'E-commerce Tip video during your trial',
    'Hi {{name}}, watch this tip while on trial: {{tipVideoTitle}}. {{tipVideoLink}} {{promoLine}}',
    '<p>Hi {{name}},</p><p>Watch this tip during your trial: <strong>{{tipVideoTitle}}</strong>.</p><p><a href="{{tipVideoLink}}">Watch E-commerce Tips</a></p><p>{{promoLine}}</p>',
    '["name","email","promoCode","promoLine","loginLink","tipVideoTitle","tipVideoLink","appName"]'::jsonb,
    TRUE
),
(
    gen_random_uuid(),
    'campaign-trial-active-whatsapp',
    'WhatsApp tip for trial users',
    'WHATSAPP',
    'TRIAL_ACTIVE',
    NULL,
    'Hi {{name}}, tip video: {{tipVideoTitle}}. {{tipVideoLink}} {{promoLine}}',
    NULL,
    '["name","email","promoCode","promoLine","loginLink","tipVideoTitle","tipVideoLink","appName"]'::jsonb,
    TRUE
)
ON CONFLICT (slug) DO NOTHING;

-- Market Tips on all plans without changing list prices (₹49 / ₹199 / ₹299 / ₹499 / ₹999).
UPDATE subscription_plans
SET features_json = COALESCE(features_json, '{}'::jsonb) || '{
  "ecommerceTips": "E-commerce Tips videos for subscribed sellers"
}'::jsonb
WHERE status = 'ACTIVE';
