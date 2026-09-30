-- Campaign notification templates + runs, and account self-deactivation / purge support.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_users_deactivated_at ON users (deactivated_at)
    WHERE deactivated_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS notification_templates (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE,
    slug            VARCHAR(80) NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    channel         VARCHAR(20) NOT NULL,
    campaign_type   VARCHAR(40) NOT NULL,
    subject         VARCHAR(255),
    body_text       TEXT NOT NULL,
    body_html       TEXT,
    variables_json  JSONB NOT NULL DEFAULT '[]'::jsonb,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_notification_templates_channel CHECK (channel IN ('EMAIL', 'WHATSAPP')),
    CONSTRAINT chk_notification_templates_type CHECK (
        campaign_type IN ('NO_PURCHASE', 'TRIAL_EXPIRED', 'EXPIRING_SOON')
    )
);

CREATE TABLE IF NOT EXISTS campaign_runs (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE,
    campaign_type       VARCHAR(40) NOT NULL,
    channel             VARCHAR(20) NOT NULL,
    promo_code          VARCHAR(40),
    triggered_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_recipients    INT NOT NULL DEFAULT 0,
    sent_count          INT NOT NULL DEFAULT 0,
    skipped_count       INT NOT NULL DEFAULT 0,
    failed_count        INT NOT NULL DEFAULT 0,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    CONSTRAINT chk_campaign_runs_channel CHECK (channel IN ('EMAIL', 'WHATSAPP', 'BOTH')),
    CONSTRAINT chk_campaign_runs_type CHECK (
        campaign_type IN ('NO_PURCHASE', 'TRIAL_EXPIRED', 'EXPIRING_SOON')
    ),
    CONSTRAINT chk_campaign_runs_status CHECK (
        status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')
    )
);

CREATE TABLE IF NOT EXISTS campaign_run_items (
    id               BIGSERIAL PRIMARY KEY,
    campaign_run_id  BIGINT NOT NULL REFERENCES campaign_runs (id) ON DELETE CASCADE,
    user_id          BIGINT REFERENCES users (id) ON DELETE SET NULL,
    email            VARCHAR(255),
    mobile           VARCHAR(20),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    skip_reason      VARCHAR(255),
    error_message    TEXT,
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_campaign_run_items_status CHECK (
        status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_campaign_run_items_pending
    ON campaign_run_items (status, id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_campaign_runs_created ON campaign_runs (created_at DESC);

-- Seed email + WhatsApp body templates (WhatsApp send needs API + user mobile).
INSERT INTO notification_templates (uuid, slug, name, channel, campaign_type, subject, body_text, body_html, variables_json, enabled)
VALUES
(
    gen_random_uuid(),
    'campaign-no-purchase-email',
    'Offer for users without a paid plan',
    'EMAIL',
    'NO_PURCHASE',
    'Special Catalog Studio offer for you',
    'Hi {{name}}, you have not purchased a Catalog Studio subscription yet. {{promoLine}}Sign in to choose a plan: {{loginLink}}',
    '<p>Hi {{name}},</p><p>You have not purchased a Catalog Studio subscription yet.</p><p>{{promoLine}}</p><p><a href="{{loginLink}}">Open Catalog Studio</a></p>',
    '["name","email","promoCode","promoLine","loginLink","appName"]'::jsonb,
    TRUE
),
(
    gen_random_uuid(),
    'campaign-no-purchase-whatsapp',
    'WhatsApp offer for users without a paid plan',
    'WHATSAPP',
    'NO_PURCHASE',
    NULL,
    'Hi {{name}}, Catalog Studio has an offer for you. {{promoLine}}Open {{loginLink}} to subscribe.',
    NULL,
    '["name","email","promoCode","promoLine","loginLink","appName"]'::jsonb,
    TRUE
),
(
    gen_random_uuid(),
    'campaign-trial-expired-email',
    'Trial ended — purchase subscription',
    'EMAIL',
    'TRIAL_EXPIRED',
    'Your Catalog Studio trial has ended',
    'Hi {{name}}, your free trial has ended. Purchase a subscription to keep using Catalog Studio tools: {{loginLink}} {{promoLine}}',
    '<p>Hi {{name}},</p><p>Your free trial has ended. Purchase a subscription to keep using Catalog Studio.</p><p>{{promoLine}}</p><p><a href="{{loginLink}}">Choose a plan</a></p>',
    '["name","email","promoCode","promoLine","loginLink","appName"]'::jsonb,
    TRUE
),
(
    gen_random_uuid(),
    'campaign-trial-expired-whatsapp',
    'WhatsApp trial ended',
    'WHATSAPP',
    'TRIAL_EXPIRED',
    NULL,
    'Hi {{name}}, your Catalog Studio trial has ended. Subscribe here: {{loginLink}} {{promoLine}}',
    NULL,
    '["name","email","promoCode","promoLine","loginLink","appName"]'::jsonb,
    TRUE
),
(
    gen_random_uuid(),
    'campaign-expiring-soon-email',
    'Paid plan expiring soon',
    'EMAIL',
    'EXPIRING_SOON',
    'Your Catalog Studio plan expires in {{daysLeft}} day(s)',
    'Hi {{name}}, your {{plan}} plan ends on {{endDate}} ({{daysLeft}} day(s) left). Renew to avoid interruption: {{loginLink}} {{promoLine}}',
    '<p>Hi {{name}},</p><p>Your <strong>{{plan}}</strong> plan ends on <strong>{{endDate}}</strong> ({{daysLeft}} day(s) left).</p><p>{{promoLine}}</p><p><a href="{{loginLink}}">Renew now</a></p>',
    '["name","email","plan","endDate","daysLeft","promoCode","promoLine","loginLink","appName"]'::jsonb,
    TRUE
),
(
    gen_random_uuid(),
    'campaign-expiring-soon-whatsapp',
    'WhatsApp plan expiring soon',
    'WHATSAPP',
    'EXPIRING_SOON',
    NULL,
    'Hi {{name}}, your Catalog Studio {{plan}} ends on {{endDate}} ({{daysLeft}} days left). Renew: {{loginLink}} {{promoLine}}',
    NULL,
    '["name","email","plan","endDate","daysLeft","promoCode","promoLine","loginLink","appName"]'::jsonb,
    TRUE
)
ON CONFLICT (slug) DO NOTHING;
