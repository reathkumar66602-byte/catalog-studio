-- Legacy monolith wholesale cutover complete.
-- Live wholesale + goods receipts live in database catalog_studio_supplier (supplier-service).
-- Platform retains inventory credit via POST /internal/v1/inventory/wholesale-receipts only.

DROP TABLE IF EXISTS goods_receipt_items CASCADE;
DROP TABLE IF EXISTS goods_receipts CASCADE;
DROP TABLE IF EXISTS wholesale_coupon_redemptions CASCADE;
DROP TABLE IF EXISTS wholesale_coupons CASCADE;
DROP TABLE IF EXISTS wholesale_order_items CASCADE;
DROP TABLE IF EXISTS wholesale_orders CASCADE;
DROP TABLE IF EXISTS wholesale_product_images CASCADE;
DROP TABLE IF EXISTS wholesale_variants CASCADE;
DROP TABLE IF EXISTS wholesale_products CASCADE;
DROP TABLE IF EXISTS wholesale_store_categories CASCADE;
DROP TABLE IF EXISTS wholesale_stores CASCADE;
DROP TABLE IF EXISTS wholesale_categories CASCADE;

-- Orphan tables with no JPA mapping / no runtime usage
DROP TABLE IF EXISTS categories CASCADE;
DROP TABLE IF EXISTS team_memberships CASCADE;
