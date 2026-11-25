-- Catalog Studio platform DB (catalog_studio). Schema: public.
-- Wholesale / goods-receipt tables live in catalog_studio_supplier (see select-supplier-tables.sql).

-- Auth / account
SELECT * FROM users;
SELECT * FROM businesses;
SELECT * FROM user_sessions;
SELECT * FROM email_verification_tokens;
SELECT * FROM password_reset_tokens;
SELECT * FROM otp_challenges;

-- Catalog / inventory
SELECT * FROM products;
SELECT * FROM product_images;
SELECT * FROM product_variants;
SELECT * FROM product_analysis;
SELECT * FROM product_attributes;
SELECT * FROM product_titles;
SELECT * FROM listing_templates;
SELECT * FROM autofill_profiles;
SELECT * FROM inventory_levels;
SELECT * FROM inventory_movements;

-- Marketplace / OMS
SELECT * FROM marketplace_connections;
SELECT * FROM marketplace_attribute_mappings;
SELECT * FROM marketplace_listings;
SELECT * FROM marketplace_channel_orders;
SELECT * FROM marketplace_sync_runs;
SELECT * FROM oms_auto_actions;
SELECT * FROM oms_courier_shipments;

-- Chrome extension
SELECT * FROM extension_devices;
SELECT * FROM extension_activity_logs;
SELECT * FROM extension_user_settings;
SELECT * FROM extension_inventory_photos;

-- Billing
SELECT * FROM subscription_plans;
SELECT * FROM subscriptions;
SELECT * FROM payment_transactions;
SELECT * FROM payment_webhook_events;
SELECT * FROM billing_settings;
SELECT * FROM billing_promo_codes;
SELECT * FROM referral_codes;
SELECT * FROM referrals;

-- Site / CRM / campaigns / tips / trending
SELECT * FROM site_settings;
SELECT * FROM clients;
SELECT * FROM client_promo_codes;
SELECT * FROM enquiries;
SELECT * FROM email_templates;
SELECT * FROM campaign_runs;
SELECT * FROM campaign_run_items;
SELECT * FROM notification_templates;
SELECT * FROM tip_videos;
SELECT * FROM tip_video_comments;
SELECT * FROM tip_video_settings;
SELECT * FROM trending_feed_items;
SELECT * FROM trending_feed_state;
SELECT * FROM user_trending_products;
SELECT * FROM user_feature_access;
SELECT * FROM product_shoots;
SELECT * FROM product_shoot_images;

-- Audit
SELECT * FROM audit_logs;

-- Flyway
SELECT * FROM flyway_schema_history;
