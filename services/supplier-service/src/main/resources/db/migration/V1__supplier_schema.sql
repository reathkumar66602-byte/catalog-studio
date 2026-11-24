-- Supplier-service owned schema (wholesale_*). No FK to platform users table.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

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

CREATE TABLE wholesale_stores (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    owner_user_id           BIGINT NOT NULL UNIQUE,
    store_name              VARCHAR(160) NOT NULL,
    slug                    VARCHAR(180) NOT NULL UNIQUE,
    gstin                   VARCHAR(15) NOT NULL,
    gst_legal_name          VARCHAR(255),
    gst_verified_at         TIMESTAMPTZ,
    gst_verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    description             TEXT,
    city                    VARCHAR(120),
    state                   VARCHAR(120),
    address_line            VARCHAR(500),
    pincode                 VARCHAR(12),
    pan                     VARCHAR(20),
    phone                   VARCHAR(20),
    email                   VARCHAR(255),
    bank_account_name       VARCHAR(160),
    bank_account_number     VARCHAR(40),
    bank_name               VARCHAR(120),
    bank_ifsc               VARCHAR(20),
    bank_branch             VARCHAR(120),
    invoice_terms           TEXT,
    gst_percent             NUMERIC(5, 2) NOT NULL DEFAULT 18.00,
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

CREATE TABLE wholesale_orders (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    order_number            VARCHAR(40) NOT NULL UNIQUE,
    buyer_user_id           BIGINT,
    store_id                BIGINT NOT NULL REFERENCES wholesale_stores (id) ON DELETE CASCADE,
    status                  VARCHAR(32) NOT NULL DEFAULT 'PLACED',
    source                  VARCHAR(16) NOT NULL DEFAULT 'ONLINE',
    buyer_note              TEXT,
    supplier_note           TEXT,
    subtotal                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    accepted_total          NUMERIC(14, 2),
    discount_amount         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    taxable_amount          NUMERIC(14, 2),
    cgst_amount             NUMERIC(14, 2) NOT NULL DEFAULT 0,
    sgst_amount             NUMERIC(14, 2) NOT NULL DEFAULT 0,
    gst_percent             NUMERIC(5, 2),
    grand_total             NUMERIC(14, 2),
    coupon_code             VARCHAR(40),
    buyer_name              VARCHAR(160),
    buyer_email             VARCHAR(255),
    buyer_mobile            VARCHAR(20),
    buyer_mobile_updated_at TIMESTAMPTZ,
    buyer_gstin             VARCHAR(20),
    buyer_address           VARCHAR(500),
    bill_pdf_url            VARCHAR(500),
    bill_pdf_storage_key    VARCHAR(255),
    bill_emailed_at         TIMESTAMPTZ,
    paid_at                 TIMESTAMPTZ,
    currency                VARCHAR(3) NOT NULL DEFAULT 'INR',
    placed_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at             TIMESTAMPTZ,
    rejected_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_wholesale_orders_status CHECK (
        status IN ('PLACED', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'FULFILLED')
    ),
    CONSTRAINT chk_wholesale_orders_source CHECK (source IN ('ONLINE', 'MANUAL'))
);

CREATE INDEX idx_wholesale_orders_buyer_placed ON wholesale_orders (buyer_user_id, placed_at DESC);
CREATE INDEX idx_wholesale_orders_store_status_placed ON wholesale_orders (store_id, status, placed_at DESC);
CREATE INDEX idx_wholesale_orders_store_source ON wholesale_orders (store_id, source, placed_at DESC);
CREATE INDEX idx_wholesale_orders_placed_at ON wholesale_orders (placed_at);

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
    hsn_sac                 VARCHAR(16),
    unit_price              NUMERIC(12, 2) NOT NULL,
    accepted_unit_price     NUMERIC(12, 2),
    quantity                INT NOT NULL,
    line_total              NUMERIC(14, 2) NOT NULL,
    CONSTRAINT chk_wholesale_order_items_qty CHECK (quantity > 0)
);

CREATE INDEX idx_wholesale_order_items_order ON wholesale_order_items (order_id);
CREATE INDEX idx_wholesale_order_items_variant ON wholesale_order_items (variant_id);

CREATE TABLE wholesale_coupons (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    store_id        BIGINT NOT NULL REFERENCES wholesale_stores (id) ON DELETE CASCADE,
    code            VARCHAR(40) NOT NULL,
    discount_type   VARCHAR(16) NOT NULL,
    discount_value  NUMERIC(14, 2) NOT NULL,
    min_order       NUMERIC(14, 2) NOT NULL DEFAULT 0,
    max_discount    NUMERIC(14, 2),
    starts_at       TIMESTAMPTZ,
    ends_at         TIMESTAMPTZ,
    usage_limit     INT,
    used_count      INT NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wholesale_coupons_store_code UNIQUE (store_id, code),
    CONSTRAINT chk_wholesale_coupons_type CHECK (discount_type IN ('PERCENT', 'FIXED')),
    CONSTRAINT chk_wholesale_coupons_value CHECK (discount_value > 0)
);

CREATE INDEX idx_wholesale_coupons_store ON wholesale_coupons (store_id);

CREATE TABLE wholesale_coupon_redemptions (
    id               BIGSERIAL PRIMARY KEY,
    uuid             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    order_id         BIGINT NOT NULL UNIQUE REFERENCES wholesale_orders (id) ON DELETE CASCADE,
    coupon_id        BIGINT NOT NULL REFERENCES wholesale_coupons (id) ON DELETE RESTRICT,
    discount_amount  NUMERIC(14, 2) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wholesale_coupon_redemptions_coupon ON wholesale_coupon_redemptions (coupon_id);
