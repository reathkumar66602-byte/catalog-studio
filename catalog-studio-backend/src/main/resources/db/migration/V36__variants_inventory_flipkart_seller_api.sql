-- Phase 1: product variants + CS-owned inventory.
-- Flipkart seller-API connection scaffolding (credentials in marketplace_connections.configuration_json).

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS sku VARCHAR(80),
    ADD COLUMN IF NOT EXISTS base_price NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS mrp NUMERIC(12, 2);

CREATE UNIQUE INDEX IF NOT EXISTS uq_products_user_sku
    ON products (user_id, sku)
    WHERE sku IS NOT NULL AND btrim(sku) <> '';

CREATE TABLE IF NOT EXISTS product_variants (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    product_id      BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku             VARCHAR(80) NOT NULL,
    size_label      VARCHAR(40),
    color_label     VARCHAR(80),
    barcode         VARCHAR(80),
    price           NUMERIC(12, 2),
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_product_variants_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT uq_product_variants_product_sku UNIQUE (product_id, sku)
);

CREATE INDEX IF NOT EXISTS idx_product_variants_product ON product_variants (product_id);

CREATE TABLE IF NOT EXISTS inventory_levels (
    id              BIGSERIAL PRIMARY KEY,
    variant_id      BIGINT NOT NULL UNIQUE REFERENCES product_variants (id) ON DELETE CASCADE,
    location_code   VARCHAR(40) NOT NULL DEFAULT 'DEFAULT',
    on_hand         INT NOT NULL DEFAULT 0,
    reserved        INT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_inventory_on_hand CHECK (on_hand >= 0),
    CONSTRAINT chk_inventory_reserved CHECK (reserved >= 0)
);

CREATE TABLE IF NOT EXISTS inventory_movements (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    variant_id      BIGINT NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    movement_type   VARCHAR(32) NOT NULL,
    quantity        INT NOT NULL,
    on_hand_after   INT NOT NULL,
    reserved_after  INT NOT NULL,
    reference       VARCHAR(120),
    note            TEXT,
    created_by      BIGINT REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_inventory_movements_type CHECK (
        movement_type IN ('ADJUST', 'RESERVE', 'RELEASE', 'SALE', 'RETURN')
    )
);

CREATE INDEX IF NOT EXISTS idx_inventory_movements_variant ON inventory_movements (variant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS marketplace_listings (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    variant_id              BIGINT NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    connection_id           BIGINT NOT NULL REFERENCES marketplace_connections (id) ON DELETE CASCADE,
    external_listing_id     VARCHAR(120),
    external_sku            VARCHAR(120),
    price                   NUMERIC(12, 2),
    stock_mirror            INT,
    status                  VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    last_synced_at          TIMESTAMPTZ,
    last_error              TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_marketplace_listings_status CHECK (
        status IN ('DRAFT', 'LIVE', 'PAUSED', 'ERROR')
    ),
    CONSTRAINT uq_marketplace_listings_variant_connection UNIQUE (variant_id, connection_id)
);

CREATE INDEX IF NOT EXISTS idx_marketplace_listings_connection ON marketplace_listings (connection_id);
