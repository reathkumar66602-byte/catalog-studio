-- Meesho × Base-style panel login OMS: channel orders + sync runs.
-- Credentials stay in marketplace_connections.configuration_json (PANEL_LOGIN).

CREATE TABLE IF NOT EXISTS marketplace_channel_orders (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    connection_id           BIGINT NOT NULL REFERENCES marketplace_connections (id) ON DELETE CASCADE,
    user_id                 BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    marketplace             VARCHAR(40) NOT NULL DEFAULT 'MEESHO',
    external_order_id       VARCHAR(120) NOT NULL,
    external_sub_order_id   VARCHAR(120) NOT NULL DEFAULT '',
    status                  VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    sku                     VARCHAR(120),
    quantity                INT NOT NULL DEFAULT 1,
    buyer_name              VARCHAR(160),
    shipping_city           VARCHAR(120),
    awb                     VARCHAR(80),
    label_url               TEXT,
    label_status            VARCHAR(32) NOT NULL DEFAULT 'NONE',
    raw_json                JSONB NOT NULL DEFAULT '{}'::jsonb,
    ordered_at              TIMESTAMPTZ,
    last_synced_at          TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_mco_label_status CHECK (
        label_status IN ('NONE', 'PENDING', 'READY', 'ERROR')
    ),
    CONSTRAINT uq_mco_connection_order UNIQUE (connection_id, external_order_id, external_sub_order_id)
);

CREATE INDEX IF NOT EXISTS idx_mco_user_status
    ON marketplace_channel_orders (user_id, status, ordered_at DESC);

CREATE INDEX IF NOT EXISTS idx_mco_connection
    ON marketplace_channel_orders (connection_id, last_synced_at DESC);

CREATE TABLE IF NOT EXISTS marketplace_sync_runs (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    connection_id   BIGINT NOT NULL REFERENCES marketplace_connections (id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    marketplace     VARCHAR(40) NOT NULL DEFAULT 'MEESHO',
    sync_type       VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    items_ok        INT NOT NULL DEFAULT 0,
    items_failed    INT NOT NULL DEFAULT 0,
    message         TEXT,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at     TIMESTAMPTZ,
    CONSTRAINT chk_msr_sync_type CHECK (
        sync_type IN ('ORDERS', 'STOCK', 'LABELS', 'FULL')
    ),
    CONSTRAINT chk_msr_status CHECK (
        status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_msr_user_started
    ON marketplace_sync_runs (user_id, started_at DESC);
