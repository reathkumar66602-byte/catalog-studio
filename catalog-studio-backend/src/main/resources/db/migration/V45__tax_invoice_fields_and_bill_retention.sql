-- Professional tax-invoice fields + 1-year bill retention support.

ALTER TABLE wholesale_stores
    ADD COLUMN IF NOT EXISTS address_line VARCHAR(500),
    ADD COLUMN IF NOT EXISTS pincode VARCHAR(12),
    ADD COLUMN IF NOT EXISTS pan VARCHAR(20),
    ADD COLUMN IF NOT EXISTS bank_account_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(40),
    ADD COLUMN IF NOT EXISTS bank_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS bank_ifsc VARCHAR(20),
    ADD COLUMN IF NOT EXISTS bank_branch VARCHAR(120),
    ADD COLUMN IF NOT EXISTS invoice_terms TEXT;

ALTER TABLE wholesale_orders
    ADD COLUMN IF NOT EXISTS buyer_gstin VARCHAR(20),
    ADD COLUMN IF NOT EXISTS buyer_address VARCHAR(500);

ALTER TABLE wholesale_order_items
    ADD COLUMN IF NOT EXISTS hsn_sac VARCHAR(16);

CREATE INDEX IF NOT EXISTS idx_wholesale_orders_placed_at ON wholesale_orders (placed_at);
