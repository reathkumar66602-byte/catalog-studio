-- Wholesale marketplace (Kartick-style live stock): suppliers, stores, products, variants, orders.
-- Separate from marketplace_* (channel OMS). Public IDs are UUIDs; internal joins use BIGINT.

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
CREATE TABLE wholesale_categories (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name            VARCHAR(120) NOT NULL,
    slug            VARCHAR(140) NOT NULL UNIQUE,
    sort_order      INT NOT NULL DEFAULT 0,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    product_count   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wholesale_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_wholesale_categories_count CHECK (product_count >= 0)
);

CREATE INDEX idx_wholesale_categories_slug ON wholesale_categories (slug);
CREATE INDEX idx_wholesale_categories_status_sort ON wholesale_categories (status, sort_order);

INSERT INTO wholesale_categories (name, slug, sort_order) VALUES
    ('Kids Wear', 'kids-wear', 10),
    ('Men''s Wear', 'mens-wear', 20),
    ('Ladies Wear', 'ladies-wear', 30),
    ('Footwear', 'footwear', 40),
    ('Accessories', 'accessories', 50),
    ('Home & Kitchen', 'home-kitchen', 60),
    ('Electronics', 'electronics', 70),
    ('General Merchandise', 'general-merchandise', 80)
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Stores (one per supplier user)
-- ---------------------------------------------------------------------------
CREATE TABLE wholesale_stores (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    owner_user_id           BIGINT NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    store_name              VARCHAR(160) NOT NULL,
    slug                    VARCHAR(180) NOT NULL UNIQUE,
    gstin                   VARCHAR(15) NOT NULL,
    gst_legal_name          VARCHAR(255),
    gst_verified_at         TIMESTAMPTZ,
    gst_verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    description             TEXT,
    city                    VARCHAR(120),
    state                   VARCHAR(120),
    phone                   VARCHAR(20),
    email                   VARCHAR(255),
    status                  VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    product_count           INT NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wholesale_stores_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_wholesale_stores_gst_status CHECK (
        gst_verification_status IN ('PENDING', 'VERIFIED', 'FAILED')
    ),
    CONSTRAINT chk_wholesale_stores_count CHECK (product_count >= 0)
);

CREATE UNIQUE INDEX uq_wholesale_stores_gstin ON wholesale_stores (gstin);
CREATE INDEX idx_wholesale_stores_status ON wholesale_stores (status);

CREATE TABLE wholesale_store_categories (
    store_id        BIGINT NOT NULL REFERENCES wholesale_stores (id) ON DELETE CASCADE,
    category_id     BIGINT NOT NULL REFERENCES wholesale_categories (id) ON DELETE CASCADE,
    PRIMARY KEY (store_id, category_id)
);

CREATE INDEX idx_wholesale_store_categories_category ON wholesale_store_categories (category_id, store_id);

-- ---------------------------------------------------------------------------
-- Products / images / variants
-- ---------------------------------------------------------------------------
CREATE TABLE wholesale_products (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    store_id            BIGINT NOT NULL REFERENCES wholesale_stores (id) ON DELETE CASCADE,
    category_id         BIGINT NOT NULL REFERENCES wholesale_categories (id),
    title               VARCHAR(255) NOT NULL,
    slug                VARCHAR(300) NOT NULL UNIQUE,
    product_code        VARCHAR(60) NOT NULL,
    description         TEXT,
    details_text        TEXT,
    mrp                 NUMERIC(12, 2),
    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    booked_count        INT NOT NULL DEFAULT 0,
    min_price           NUMERIC(12, 2),
    max_price           NUMERIC(12, 2),
    total_stock         INT NOT NULL DEFAULT 0,
    primary_image_url   TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wholesale_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_wholesale_products_booked CHECK (booked_count >= 0),
    CONSTRAINT chk_wholesale_products_stock CHECK (total_stock >= 0),
    CONSTRAINT uq_wholesale_products_store_code UNIQUE (store_id, product_code)
);

CREATE INDEX idx_wholesale_products_category_status_created
    ON wholesale_products (category_id, status, created_at DESC);
CREATE INDEX idx_wholesale_products_status_created
    ON wholesale_products (status, created_at DESC);
CREATE INDEX idx_wholesale_products_store_status
    ON wholesale_products (store_id, status);

CREATE TABLE wholesale_product_images (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    product_id      BIGINT NOT NULL REFERENCES wholesale_products (id) ON DELETE CASCADE,
    storage_key     VARCHAR(255) NOT NULL,
    public_url      TEXT NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    color_label     VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wholesale_product_images_product ON wholesale_product_images (product_id, sort_order);

CREATE TABLE wholesale_variants (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    product_id      BIGINT NOT NULL REFERENCES wholesale_products (id) ON DELETE CASCADE,
    sku             VARCHAR(120) NOT NULL,
    color_label     VARCHAR(80) NOT NULL DEFAULT 'Default',
    size_label      VARCHAR(40) NOT NULL DEFAULT 'Free',
    color_hex       VARCHAR(9),
    price           NUMERIC(12, 2) NOT NULL,
    mrp             NUMERIC(12, 2),
    on_hand         INT NOT NULL DEFAULT 0,
    reserved        INT NOT NULL DEFAULT 0,
    version         INT NOT NULL DEFAULT 0,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wholesale_variants_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_wholesale_variants_on_hand CHECK (on_hand >= 0),
    CONSTRAINT chk_wholesale_variants_reserved CHECK (reserved >= 0 AND reserved <= on_hand),
    CONSTRAINT chk_wholesale_variants_price CHECK (price >= 0),
    CONSTRAINT uq_wholesale_variants_product_sku UNIQUE (product_id, sku)
);

CREATE INDEX idx_wholesale_variants_product ON wholesale_variants (product_id);
CREATE UNIQUE INDEX uq_wholesale_variants_product_color_size
    ON wholesale_variants (product_id, lower(color_label), lower(size_label));

-- ---------------------------------------------------------------------------
-- Orders
-- ---------------------------------------------------------------------------
CREATE TABLE wholesale_orders (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    order_number    VARCHAR(40) NOT NULL UNIQUE,
    buyer_user_id   BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    store_id        BIGINT NOT NULL REFERENCES wholesale_stores (id) ON DELETE CASCADE,
    status          VARCHAR(32) NOT NULL DEFAULT 'PLACED',
    buyer_note      TEXT,
    supplier_note   TEXT,
    subtotal        NUMERIC(14, 2) NOT NULL DEFAULT 0,
    accepted_total  NUMERIC(14, 2),
    currency        VARCHAR(3) NOT NULL DEFAULT 'INR',
    placed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at     TIMESTAMPTZ,
    rejected_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wholesale_orders_status CHECK (
        status IN ('PLACED', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'FULFILLED')
    )
);

CREATE INDEX idx_wholesale_orders_buyer_placed ON wholesale_orders (buyer_user_id, placed_at DESC);
CREATE INDEX idx_wholesale_orders_store_status_placed ON wholesale_orders (store_id, status, placed_at DESC);

CREATE TABLE wholesale_order_items (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    order_id                BIGINT NOT NULL REFERENCES wholesale_orders (id) ON DELETE CASCADE,
    variant_id              BIGINT NOT NULL REFERENCES wholesale_variants (id) ON DELETE CASCADE,
    product_id              BIGINT NOT NULL REFERENCES wholesale_products (id) ON DELETE CASCADE,
    product_title           VARCHAR(255) NOT NULL,
    color_label             VARCHAR(80),
    size_label              VARCHAR(40),
    sku                     VARCHAR(120),
    unit_price              NUMERIC(12, 2) NOT NULL,
    accepted_unit_price     NUMERIC(12, 2),
    quantity                INT NOT NULL,
    line_total              NUMERIC(14, 2) NOT NULL,
    CONSTRAINT chk_wholesale_order_items_qty CHECK (quantity > 0)
);

CREATE INDEX idx_wholesale_order_items_order ON wholesale_order_items (order_id);
CREATE INDEX idx_wholesale_order_items_variant ON wholesale_order_items (variant_id);

-- ---------------------------------------------------------------------------
-- Plans: SUPPLIER plan + feature flags (idempotent; existing keys preserved)
-- ---------------------------------------------------------------------------
INSERT INTO subscription_plans (name, price, billing_cycle, features_json, status)
SELECT 'SUPPLIER', 999, 'MONTHLY',
'{"monthlyAiAnalyses": 50, "products": 500, "extensionDevices": 2, "teamMembers": 1, "marketplaces": ["MEESHO"], "wholesaleSupplier": true, "liveStock": true}'::jsonb,
'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM subscription_plans WHERE name = 'SUPPLIER');

-- Enable supplier capability on BUSINESS too without removing existing keys
UPDATE subscription_plans
SET features_json = COALESCE(features_json, '{}'::jsonb) || '{"wholesaleSupplier": true, "liveStock": true}'::jsonb
WHERE name = 'BUSINESS';

-- Live stock browsing for every plan that exists (FREE, BASIC, PLUS, STARTER, PRO, BUSINESS, SUPPLIER, ...)
UPDATE subscription_plans
SET features_json = COALESCE(features_json, '{}'::jsonb) || '{"liveStock": true}'::jsonb
WHERE name IN ('FREE', 'BASIC', 'PLUS', 'STARTER', 'PRO', 'BUSINESS', 'SUPPLIER');
