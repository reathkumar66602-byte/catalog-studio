-- Catalog Studio: SELECT every application table (plus Flyway history).
-- Run against database catalog_studio.

-- Auth / account
SELECT * FROM users;
SELECT * FROM businesses;
SELECT * FROM team_memberships;
SELECT * FROM user_sessions;
SELECT * FROM email_verification_tokens;
SELECT * FROM password_reset_tokens;
SELECT * FROM otp_challenges;

-- Catalog
SELECT * FROM products;
SELECT * FROM product_images;
SELECT * FROM product_analysis;
SELECT * FROM product_attributes;
SELECT * FROM product_titles;
SELECT * FROM categories;
SELECT * FROM listing_templates;
SELECT * FROM autofill_profiles;

-- Marketplace
SELECT * FROM marketplace_connections;
SELECT * FROM marketplace_attribute_mappings;

-- Chrome extension
SELECT * FROM extension_devices;
SELECT * FROM extension_activity_logs;
SELECT * FROM extension_user_settings;
SELECT * FROM extension_inventory_photos;

-- Billing
SELECT * FROM subscription_plans;
SELECT * FROM subscriptions;
SELECT * FROM payment_transactions;
SELECT * FROM billing_settings;
SELECT * FROM referral_codes;
SELECT * FROM referrals;

-- Site / CRM
SELECT * FROM site_settings;
SELECT * FROM clients;
SELECT * FROM client_promo_codes;
SELECT * FROM enquiries;
SELECT * FROM email_templates;

-- Audit
SELECT * FROM audit_logs;

-- Flyway
SELECT * FROM flyway_schema_history;
