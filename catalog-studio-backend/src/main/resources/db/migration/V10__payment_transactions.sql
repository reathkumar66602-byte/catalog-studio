CREATE TABLE payment_transactions (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_id         BIGINT REFERENCES subscription_plans (id) ON DELETE SET NULL,
    plan_name       VARCHAR(40) NOT NULL,
    amount          NUMERIC(10, 2) NOT NULL DEFAULT 0,
    currency        VARCHAR(8) NOT NULL DEFAULT 'INR',
    provider        VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    reference       VARCHAR(120),
    type            VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    billing_cycle   VARCHAR(20),
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_txn_type CHECK (type IN ('TRIAL', 'CHECKOUT', 'PAYMENT_SENT', 'ACTIVATION')),
    CONSTRAINT chk_payment_txn_status CHECK (status IN ('PENDING', 'REPORTED', 'SUCCESS', 'FAILED', 'EXPIRED'))
);

CREATE INDEX idx_payment_txn_user_created ON payment_transactions (user_id, created_at DESC);

INSERT INTO payment_transactions (
    uuid, user_id, plan_id, plan_name, amount, currency, provider, reference,
    type, status, billing_cycle, notes, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    s.user_id,
    s.plan_id,
    p.name,
    CASE WHEN s.status = 'TRIAL' THEN 0 ELSE COALESCE(p.price, 0) END,
    'INR',
    'MANUAL',
    s.uuid::text,
    CASE
        WHEN s.status = 'TRIAL' THEN 'TRIAL'
        WHEN s.status = 'PAYMENT_PENDING' THEN 'PAYMENT_SENT'
        ELSE 'CHECKOUT'
    END,
    CASE
        WHEN s.status IN ('TRIAL', 'ACTIVE') THEN 'SUCCESS'
        WHEN s.status = 'PAYMENT_PENDING' THEN 'REPORTED'
        WHEN s.status = 'EXPIRED' THEN 'EXPIRED'
        ELSE 'PENDING'
    END,
    p.billing_cycle,
    CASE
        WHEN s.status = 'TRIAL' THEN 'Signup trial'
        WHEN s.status = 'PAYMENT_PENDING' THEN 'Payment screenshot reported'
        WHEN s.status = 'ACTIVE' THEN 'Active plan'
        ELSE 'Imported from subscription'
    END,
    s.created_at,
    s.updated_at
FROM subscriptions s
JOIN subscription_plans p ON p.id = s.plan_id;
