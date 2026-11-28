-- Base.com-style Meesho OMS hardening: offer links, reservation flags, sync types, returns.

ALTER TABLE marketplace_listings
    ADD COLUMN IF NOT EXISTS last_pushed_stock INT,
    ADD COLUMN IF NOT EXISTS last_pushed_price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS offer_title VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_marketplace_listings_external_sku
    ON marketplace_listings (connection_id, external_sku);

ALTER TABLE marketplace_channel_orders
    ADD COLUMN IF NOT EXISTS inventory_reserved BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS oms_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS return_status VARCHAR(40);

ALTER TABLE marketplace_sync_runs DROP CONSTRAINT IF EXISTS chk_msr_sync_type;
ALTER TABLE marketplace_sync_runs
    ADD CONSTRAINT chk_msr_sync_type CHECK (
        sync_type IN (
            'ORDERS', 'STOCK', 'LABELS', 'FULL',
            'OFFERS', 'RETURNS', 'SHIP', 'PRICES', 'STATUS'
        )
    );

CREATE TABLE IF NOT EXISTS oms_status_mappings (
    id                  BIGSERIAL PRIMARY KEY,
    marketplace         VARCHAR(40) NOT NULL,
    internal_status     VARCHAR(40) NOT NULL,
    channel_status      VARCHAR(80) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_oms_status_mapping UNIQUE (marketplace, internal_status)
);

INSERT INTO oms_status_mappings (marketplace, internal_status, channel_status)
VALUES
    ('MEESHO', 'NEW', 'Pending'),
    ('MEESHO', 'PROCESSING', 'Accepted'),
    ('MEESHO', 'PACKED', 'Ready to ship'),
    ('MEESHO', 'SHIPPED', 'Dispatched'),
    ('MEESHO', 'DELIVERED', 'Delivered'),
    ('MEESHO', 'CANCELLED', 'Cancelled'),
    ('MEESHO', 'RETURN', 'Return')
ON CONFLICT (marketplace, internal_status) DO NOTHING;
