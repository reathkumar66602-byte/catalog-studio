-- HISTORICAL one-time cutover script (do not re-run on cleaned environments).
-- After Flyway V51, platform catalog_studio no longer has wholesale_* tables.
-- Live wholesale data is only in catalog_studio_supplier.
--
-- Original purpose: copy wholesale_* from catalog_studio into catalog_studio_supplier.
-- Run as catalog_studio against catalog_studio_supplier AFTER supplier Flyway has created empty tables.

CREATE EXTENSION IF NOT EXISTS dblink;

SELECT dblink_connect(
  'platform',
  'dbname=catalog_studio user=catalog_studio password=' || current_setting('app.platform_password', true)
);

-- Password is passed by the deploy script via: SET app.platform_password = '...';

TRUNCATE TABLE
    wholesale_coupon_redemptions,
    wholesale_coupons,
    wholesale_order_items,
    wholesale_orders,
    wholesale_variants,
    wholesale_product_images,
    wholesale_products,
    wholesale_store_categories,
    wholesale_stores,
    wholesale_categories
RESTART IDENTITY CASCADE;

INSERT INTO wholesale_categories (id, uuid, name, slug, sort_order, status, product_count, created_at)
SELECT * FROM dblink('platform',
  'SELECT id, uuid, name, slug, sort_order, status, product_count, created_at FROM wholesale_categories')
AS t(id bigint, uuid uuid, name varchar, slug varchar, sort_order int, status varchar, product_count int, created_at timestamptz);

INSERT INTO wholesale_stores (
  id, uuid, owner_user_id, store_name, slug, gstin, gst_legal_name, gst_verified_at, gst_verification_status,
  description, city, state, address_line, pincode, pan, phone, email,
  bank_account_name, bank_account_number, bank_name, bank_ifsc, bank_branch, invoice_terms,
  gst_percent, status, product_count, created_at, updated_at
)
SELECT * FROM dblink('platform',
  'SELECT id, uuid, owner_user_id, store_name, slug, gstin, gst_legal_name, gst_verified_at, gst_verification_status,
          description, city, state, address_line, pincode, pan, phone, email,
          bank_account_name, bank_account_number, bank_name, bank_ifsc, bank_branch, invoice_terms,
          gst_percent, status, product_count, created_at, updated_at
   FROM wholesale_stores')
AS t(
  id bigint, uuid uuid, owner_user_id bigint, store_name varchar, slug varchar, gstin varchar, gst_legal_name varchar,
  gst_verified_at timestamptz, gst_verification_status varchar, description text, city varchar, state varchar,
  address_line varchar, pincode varchar, pan varchar, phone varchar, email varchar,
  bank_account_name varchar, bank_account_number varchar, bank_name varchar, bank_ifsc varchar, bank_branch varchar,
  invoice_terms text, gst_percent numeric, status varchar, product_count int, created_at timestamptz, updated_at timestamptz
);

INSERT INTO wholesale_store_categories (store_id, category_id)
SELECT * FROM dblink('platform', 'SELECT store_id, category_id FROM wholesale_store_categories')
AS t(store_id bigint, category_id bigint);

INSERT INTO wholesale_products (
  id, uuid, store_id, category_id, title, slug, product_code, description, details_text, mrp, status,
  booked_count, min_price, max_price, total_stock, primary_image_url, created_at, updated_at
)
SELECT * FROM dblink('platform',
  'SELECT id, uuid, store_id, category_id, title, slug, product_code, description, details_text, mrp, status,
          booked_count, min_price, max_price, total_stock, primary_image_url, created_at, updated_at
   FROM wholesale_products')
AS t(
  id bigint, uuid uuid, store_id bigint, category_id bigint, title varchar, slug varchar, product_code varchar,
  description text, details_text text, mrp numeric, status varchar, booked_count int, min_price numeric,
  max_price numeric, total_stock int, primary_image_url text, created_at timestamptz, updated_at timestamptz
);

INSERT INTO wholesale_product_images (id, uuid, product_id, storage_key, public_url, sort_order, color_label, created_at)
SELECT * FROM dblink('platform',
  'SELECT id, uuid, product_id, storage_key, public_url, sort_order, color_label, created_at FROM wholesale_product_images')
AS t(id bigint, uuid uuid, product_id bigint, storage_key varchar, public_url text, sort_order int, color_label varchar, created_at timestamptz);

INSERT INTO wholesale_variants (
  id, uuid, product_id, sku, color_label, size_label, color_hex, price, mrp, on_hand, reserved, version, status, created_at, updated_at
)
SELECT * FROM dblink('platform',
  'SELECT id, uuid, product_id, sku, color_label, size_label, color_hex, price, mrp, on_hand, reserved, version, status, created_at, updated_at
   FROM wholesale_variants')
AS t(
  id bigint, uuid uuid, product_id bigint, sku varchar, color_label varchar, size_label varchar, color_hex varchar,
  price numeric, mrp numeric, on_hand int, reserved int, version int, status varchar, created_at timestamptz, updated_at timestamptz
);

INSERT INTO wholesale_orders (
  id, uuid, order_number, buyer_user_id, store_id, status, source, buyer_note, supplier_note,
  subtotal, accepted_total, discount_amount, taxable_amount, cgst_amount, sgst_amount, gst_percent, grand_total,
  coupon_code, buyer_name, buyer_email, buyer_mobile, buyer_mobile_updated_at, buyer_gstin, buyer_address,
  bill_pdf_url, bill_pdf_storage_key, bill_emailed_at, paid_at, currency, placed_at, accepted_at, rejected_at, created_at, updated_at
)
SELECT * FROM dblink('platform',
  'SELECT id, uuid, order_number, buyer_user_id, store_id, status, COALESCE(source, ''ONLINE''), buyer_note, supplier_note,
          subtotal, accepted_total, COALESCE(discount_amount, 0), taxable_amount, COALESCE(cgst_amount, 0), COALESCE(sgst_amount, 0),
          gst_percent, grand_total, coupon_code, buyer_name, buyer_email, buyer_mobile, buyer_mobile_updated_at, buyer_gstin, buyer_address,
          bill_pdf_url, bill_pdf_storage_key, bill_emailed_at, paid_at, currency, placed_at, accepted_at, rejected_at, created_at, updated_at
   FROM wholesale_orders')
AS t(
  id bigint, uuid uuid, order_number varchar, buyer_user_id bigint, store_id bigint, status varchar, source varchar,
  buyer_note text, supplier_note text, subtotal numeric, accepted_total numeric, discount_amount numeric,
  taxable_amount numeric, cgst_amount numeric, sgst_amount numeric, gst_percent numeric, grand_total numeric,
  coupon_code varchar, buyer_name varchar, buyer_email varchar, buyer_mobile varchar, buyer_mobile_updated_at timestamptz,
  buyer_gstin varchar, buyer_address varchar, bill_pdf_url varchar, bill_pdf_storage_key varchar, bill_emailed_at timestamptz,
  paid_at timestamptz, currency varchar, placed_at timestamptz, accepted_at timestamptz, rejected_at timestamptz,
  created_at timestamptz, updated_at timestamptz
);

INSERT INTO wholesale_order_items (
  id, uuid, order_id, variant_id, product_id, product_title, color_label, size_label, sku, hsn_sac,
  unit_price, accepted_unit_price, quantity, line_total
)
SELECT * FROM dblink('platform',
  'SELECT id, uuid, order_id, variant_id, product_id, product_title, color_label, size_label, sku, hsn_sac,
          unit_price, accepted_unit_price, quantity, line_total
   FROM wholesale_order_items')
AS t(
  id bigint, uuid uuid, order_id bigint, variant_id bigint, product_id bigint, product_title varchar,
  color_label varchar, size_label varchar, sku varchar, hsn_sac varchar, unit_price numeric,
  accepted_unit_price numeric, quantity int, line_total numeric
);

INSERT INTO wholesale_coupons (
  id, uuid, store_id, code, discount_type, discount_value, min_order, max_discount, starts_at, ends_at,
  usage_limit, used_count, active, created_at, updated_at
)
SELECT * FROM dblink('platform',
  'SELECT id, uuid, store_id, code, discount_type, discount_value, min_order, max_discount, starts_at, ends_at,
          usage_limit, used_count, active, created_at, updated_at
   FROM wholesale_coupons')
AS t(
  id bigint, uuid uuid, store_id bigint, code varchar, discount_type varchar, discount_value numeric,
  min_order numeric, max_discount numeric, starts_at timestamptz, ends_at timestamptz, usage_limit int,
  used_count int, active boolean, created_at timestamptz, updated_at timestamptz
);

INSERT INTO wholesale_coupon_redemptions (id, uuid, order_id, coupon_id, discount_amount, created_at)
SELECT * FROM dblink('platform',
  'SELECT id, uuid, order_id, coupon_id, discount_amount, created_at FROM wholesale_coupon_redemptions')
AS t(id bigint, uuid uuid, order_id bigint, coupon_id bigint, discount_amount numeric, created_at timestamptz);

SELECT setval(pg_get_serial_sequence('wholesale_categories', 'id'), COALESCE((SELECT MAX(id) FROM wholesale_categories), 1), true);
SELECT setval(pg_get_serial_sequence('wholesale_stores', 'id'), COALESCE((SELECT MAX(id) FROM wholesale_stores), 1), true);
SELECT setval(pg_get_serial_sequence('wholesale_products', 'id'), COALESCE((SELECT MAX(id) FROM wholesale_products), 1), true);
SELECT setval(pg_get_serial_sequence('wholesale_product_images', 'id'), COALESCE((SELECT MAX(id) FROM wholesale_product_images), 1), true);
SELECT setval(pg_get_serial_sequence('wholesale_variants', 'id'), COALESCE((SELECT MAX(id) FROM wholesale_variants), 1), true);
SELECT setval(pg_get_serial_sequence('wholesale_orders', 'id'), COALESCE((SELECT MAX(id) FROM wholesale_orders), 1), true);
SELECT setval(pg_get_serial_sequence('wholesale_order_items', 'id'), COALESCE((SELECT MAX(id) FROM wholesale_order_items), 1), true);
SELECT setval(pg_get_serial_sequence('wholesale_coupons', 'id'), COALESCE((SELECT MAX(id) FROM wholesale_coupons), 1), true);
SELECT setval(pg_get_serial_sequence('wholesale_coupon_redemptions', 'id'), COALESCE((SELECT MAX(id) FROM wholesale_coupon_redemptions), 1), true);

SELECT dblink_disconnect('platform');
