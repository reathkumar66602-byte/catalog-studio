-- Manual offline bills for suppliers + PDF storage + optional walk-in buyer (no platform account).

ALTER TABLE wholesale_orders
    ALTER COLUMN buyer_user_id DROP NOT NULL;

ALTER TABLE wholesale_orders
    ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'ONLINE',
    ADD COLUMN IF NOT EXISTS buyer_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS buyer_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS bill_pdf_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS bill_pdf_storage_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS bill_emailed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

ALTER TABLE wholesale_orders
    DROP CONSTRAINT IF EXISTS chk_wholesale_orders_source;

ALTER TABLE wholesale_orders
    ADD CONSTRAINT chk_wholesale_orders_source CHECK (source IN ('ONLINE', 'MANUAL'));

CREATE INDEX IF NOT EXISTS idx_wholesale_orders_store_source ON wholesale_orders (store_id, source, placed_at DESC);
