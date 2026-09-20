-- Trial length, UPI / WhatsApp recharge, and pending-plan tracking.
-- New sellers receive a database-configured trial on first login; existing ACTIVE
-- subscriptions are not rewritten.

CREATE TABLE IF NOT EXISTS billing_settings (
    id                          BIGSERIAL PRIMARY KEY,
    settings_key                VARCHAR(40) NOT NULL UNIQUE DEFAULT 'default',
    trial_days                  INTEGER NOT NULL DEFAULT 1,
    trial_plan                  VARCHAR(40) NOT NULL DEFAULT 'PRO',
    whatsapp_number             VARCHAR(40) NOT NULL DEFAULT '919876543210',
    whatsapp_message_template   TEXT NOT NULL,
    upi_id                      VARCHAR(120) NOT NULL DEFAULT 'catalogstudio@upi',
    payee_name                  VARCHAR(160) NOT NULL DEFAULT 'Catalog Studio',
    qr_image_url                VARCHAR(500),
    payment_instructions        TEXT NOT NULL,
    recharge_headline           VARCHAR(200) NOT NULL DEFAULT 'Recharge to keep using Catalog Studio',
    recharge_body               TEXT NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_billing_trial_days CHECK (trial_days >= 0)
);

INSERT INTO billing_settings (
    settings_key,
    trial_days,
    trial_plan,
    whatsapp_number,
    whatsapp_message_template,
    upi_id,
    payee_name,
    qr_image_url,
    payment_instructions,
    recharge_headline,
    recharge_body
) VALUES (
    'default',
    1,
    'PRO',
    '919876543210',
    'Hello Catalog Studio, I have paid for the {{plan}} plan (₹{{amount}}). Registered email: {{email}}. Payment screenshot is attached.',
    'catalogstudio@upi',
    'Catalog Studio',
    NULL,
    'Scan the UPI QR, pay the plan amount, then send the payment screenshot on WhatsApp. Mention your registered email ID in the same message so we can activate the correct account.',
    'Recharge to keep using Catalog Studio',
    'Your free trial has ended. Choose a plan, pay by UPI, and send the payment screenshot on WhatsApp with your registered email. Access is enabled after we confirm the payment.'
)
ON CONFLICT (settings_key) DO NOTHING;

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS trial_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS pending_plan_id BIGINT REFERENCES subscription_plans (id);

ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS chk_sub_status;
ALTER TABLE subscriptions
    ADD CONSTRAINT chk_sub_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED', 'TRIAL', 'PAYMENT_PENDING'));

-- Change trial length (example: 3 days):
-- UPDATE billing_settings SET trial_days = 3, updated_at = NOW() WHERE settings_key = 'default';
--
-- After WhatsApp screenshot verification, activate paid access by registered email:
-- See src/main/resources/db/scripts/activate_paid_access_by_email.sql
