-- Cashfree Payment Links: ledger enrichment + webhook idempotency + default provider.

ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS gateway_link_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS gateway_cf_link_id VARCHAR(40),
    ADD COLUMN IF NOT EXISTS gateway_order_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS gateway_payment_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_transactions_gateway_link_id
    ON payment_transactions (gateway_link_id)
    WHERE gateway_link_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS payment_webhook_events (
    id              BIGSERIAL PRIMARY KEY,
    provider        VARCHAR(40)  NOT NULL,
    event_key       VARCHAR(160) NOT NULL,
    event_type      VARCHAR(80),
    link_id         VARCHAR(80),
    payload_hash    VARCHAR(64),
    processed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ux_payment_webhook_events_provider_key UNIQUE (provider, event_key)
);

CREATE INDEX IF NOT EXISTS ix_payment_webhook_events_link_id
    ON payment_webhook_events (link_id);

UPDATE billing_settings
SET payment_provider = 'CASHFREE',
    payment_instructions = COALESCE(
        NULLIF(TRIM(payment_instructions), ''),
        'Pay securely with UPI, card, or netbanking via Cashfree. Your plan activates automatically after successful payment.'
    ),
    recharge_headline = 'Your trial or plan has ended.',
    recharge_body = 'Choose a plan and complete payment online. Access unlocks automatically once Cashfree confirms the payment.',
    updated_at = NOW()
WHERE settings_key = 'default';
