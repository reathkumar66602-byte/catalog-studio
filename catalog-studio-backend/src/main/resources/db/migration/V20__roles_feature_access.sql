-- Workspace roles: USER + SUPERADMIN, per-user feature access, WhatsApp activation audit types.

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role
    CHECK (role IN ('SUPERADMIN', 'ADMIN', 'USER', 'SELLER', 'TEAM_MEMBER'));

UPDATE users
SET role = 'SUPERADMIN',
    status = 'ACTIVE',
    updated_at = NOW()
WHERE lower(email) IN ('vishalmishra66602@gmail.com', 'vishalmishra66602@gmail');

CREATE TABLE user_feature_access (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    feature_key     VARCHAR(80) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, feature_key)
);

CREATE INDEX idx_user_feature_access_user ON user_feature_access (user_id);

ALTER TABLE payment_transactions DROP CONSTRAINT IF EXISTS chk_payment_txn_type;
ALTER TABLE payment_transactions ADD CONSTRAINT chk_payment_txn_type
    CHECK (type IN ('TRIAL', 'CHECKOUT', 'PAYMENT_SENT', 'ACTIVATION', 'PLAN_CHANGE', 'DEACTIVATION'));
