-- Goods receipts live with wholesale orders. The supplier database has no users table;
-- buyer_user_id is the platform user id. Inventory is credited on the platform API.

CREATE TABLE IF NOT EXISTS goods_receipts (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    order_id        BIGINT NOT NULL UNIQUE REFERENCES wholesale_orders (id) ON DELETE CASCADE,
    buyer_user_id   BIGINT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    note            TEXT,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at    TIMESTAMPTZ,
    CONSTRAINT chk_goods_receipts_status CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_goods_receipts_buyer
    ON goods_receipts (buyer_user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS goods_receipt_items (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    receipt_id      BIGINT NOT NULL REFERENCES goods_receipts (id) ON DELETE CASCADE,
    order_item_id   BIGINT NOT NULL REFERENCES wholesale_order_items (id) ON DELETE CASCADE,
    sku             VARCHAR(120),
    product_title   VARCHAR(255) NOT NULL,
    ordered_qty     INT NOT NULL,
    received_qty    INT NOT NULL,
    missing_qty     INT NOT NULL,
    damaged_qty     INT NOT NULL,
    rejected_qty    INT NOT NULL,
    accepted_qty    INT NOT NULL,
    CONSTRAINT chk_goods_receipt_items_nonneg CHECK (
        ordered_qty > 0
        AND received_qty >= 0
        AND missing_qty >= 0
        AND damaged_qty >= 0
        AND rejected_qty >= 0
        AND accepted_qty >= 0
        AND received_qty <= ordered_qty
        AND damaged_qty + rejected_qty <= received_qty
        AND missing_qty = ordered_qty - received_qty
        AND accepted_qty = received_qty - damaged_qty - rejected_qty
    ),
    CONSTRAINT uq_goods_receipt_items_line UNIQUE (receipt_id, order_item_id)
);

CREATE INDEX IF NOT EXISTS idx_goods_receipt_items_receipt
    ON goods_receipt_items (receipt_id, id);
