-- Base-style OMS: listings entity usage, auto-actions, courier shipments.

ALTER TABLE marketplace_channel_orders
    ADD COLUMN IF NOT EXISTS channel_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(8) DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS courier_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS tracking_url TEXT;

CREATE TABLE IF NOT EXISTS oms_auto_actions (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    trigger_event   VARCHAR(64) NOT NULL,
    channel         VARCHAR(40),
    condition_json  JSONB NOT NULL DEFAULT '{}'::jsonb,
    action_json     JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_run_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_oms_auto_trigger CHECK (
        trigger_event IN (
            'ORDER_IMPORTED', 'ORDER_STATUS', 'STOCK_CHANGED', 'PRICE_CHANGED', 'MANUAL'
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_oms_auto_actions_user ON oms_auto_actions (user_id, enabled);

CREATE TABLE IF NOT EXISTS oms_courier_shipments (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel_order_id    BIGINT REFERENCES marketplace_channel_orders (id) ON DELETE SET NULL,
    provider            VARCHAR(40) NOT NULL,
    status              VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    awb                 VARCHAR(80),
    label_url           TEXT,
    tracking_url        TEXT,
    raw_json            JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_oms_courier_provider CHECK (
        provider IN ('MARKETPLACE', 'SHIPROCKET', 'DELHIVERY', 'MANUAL')
    )
);

CREATE INDEX IF NOT EXISTS idx_oms_courier_user ON oms_courier_shipments (user_id, created_at DESC);

-- Seed default auto-actions for documentation (no-op until enabled by admin).
-- Actual rows created per superadmin on first sync via service if empty.
