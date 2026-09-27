-- Company tax invoice fields, service charge, GST, billing promo codes,
-- and transaction / pending checkout price breakdown.

ALTER TABLE billing_settings
    ADD COLUMN IF NOT EXISTS company_legal_name VARCHAR(160) NOT NULL DEFAULT 'Catalog Studio',
    ADD COLUMN IF NOT EXISTS company_gstin VARCHAR(20) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS parent_company_name VARCHAR(120) NOT NULL DEFAULT 'Shirtaji',
    ADD COLUMN IF NOT EXISTS service_charge_percent NUMERIC(6, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS gst_percent NUMERIC(6, 2) NOT NULL DEFAULT 18;

UPDATE billing_settings
SET
    company_legal_name = COALESCE(NULLIF(TRIM(company_legal_name), ''), 'Catalog Studio'),
    parent_company_name = COALESCE(NULLIF(TRIM(parent_company_name), ''), 'Shirtaji'),
    gst_percent = COALESCE(gst_percent, 18),
    service_charge_percent = COALESCE(service_charge_percent, 0),
    updated_at = NOW()
WHERE settings_key = 'default';

CREATE TABLE IF NOT EXISTS billing_promo_codes (
    id                BIGSERIAL PRIMARY KEY,
    uuid              UUID         NOT NULL UNIQUE,
    code              VARCHAR(40)  NOT NULL UNIQUE,
    description       VARCHAR(255),
    discount_type     VARCHAR(20)  NOT NULL,
    discount_value    NUMERIC(10, 2) NOT NULL,
    max_uses          INTEGER,
    used_count        INTEGER      NOT NULL DEFAULT 0,
    valid_from        DATE,
    valid_until       DATE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by_user_id BIGINT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_billing_promo_discount CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT chk_billing_promo_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_billing_promo_value CHECK (discount_value >= 0)
);

CREATE INDEX IF NOT EXISTS idx_billing_promo_status ON billing_promo_codes (status, code);

ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS base_amount      NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS service_charge   NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS gst_amount       NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS discount_amount  NUMERIC(10, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS promo_code       VARCHAR(40),
    ADD COLUMN IF NOT EXISTS company_gstin    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS invoice_number   VARCHAR(40);

UPDATE payment_transactions
SET base_amount = amount
WHERE base_amount IS NULL;

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS pending_promo_code      VARCHAR(40),
    ADD COLUMN IF NOT EXISTS pending_base_amount     NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS pending_discount_amount NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS pending_service_charge  NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS pending_gst_amount      NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS pending_total_amount    NUMERIC(10, 2);
