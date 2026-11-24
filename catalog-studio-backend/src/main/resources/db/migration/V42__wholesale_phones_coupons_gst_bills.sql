-- Buyer mobile snapshot on wholesale orders + store GST rate + store coupons + bill totals.

ALTER TABLE wholesale_stores
    ADD COLUMN IF NOT EXISTS gst_percent NUMERIC(5, 2) NOT NULL DEFAULT 18.00;

ALTER TABLE wholesale_orders
    ADD COLUMN IF NOT EXISTS buyer_mobile VARCHAR(20),
    ADD COLUMN IF NOT EXISTS buyer_mobile_updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS taxable_amount NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS cgst_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS sgst_amount NUMERIC(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS gst_percent NUMERIC(5, 2),
    ADD COLUMN IF NOT EXISTS grand_total NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(40);

UPDATE wholesale_orders o
SET buyer_mobile = u.mobile
FROM users u
WHERE o.buyer_user_id = u.id
  AND o.buyer_mobile IS NULL
  AND u.mobile IS NOT NULL
  AND btrim(u.mobile) <> '';

UPDATE wholesale_orders
SET taxable_amount = GREATEST(subtotal - discount_amount, 0),
    grand_total = GREATEST(subtotal - discount_amount, 0)
WHERE taxable_amount IS NULL;

CREATE TABLE IF NOT EXISTS wholesale_coupons (
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

CREATE INDEX IF NOT EXISTS idx_wholesale_coupons_store ON wholesale_coupons (store_id);

CREATE TABLE IF NOT EXISTS wholesale_coupon_redemptions (
    id               BIGSERIAL PRIMARY KEY,
    uuid             UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    order_id         BIGINT NOT NULL UNIQUE REFERENCES wholesale_orders (id) ON DELETE CASCADE,
    coupon_id        BIGINT NOT NULL REFERENCES wholesale_coupons (id) ON DELETE RESTRICT,
    discount_amount  NUMERIC(14, 2) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wholesale_coupon_redemptions_coupon ON wholesale_coupon_redemptions (coupon_id);
