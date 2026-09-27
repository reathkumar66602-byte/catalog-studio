-- Seed Catalog Studio billing promo: KRISHNA10 (10% off).
-- Idempotent: safe to re-run on local and prod.

INSERT INTO billing_promo_codes (
    uuid,
    code,
    description,
    discount_type,
    discount_value,
    max_uses,
    used_count,
    valid_from,
    valid_until,
    status,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    'KRISHNA10',
    '10% off Catalog Studio Pro for Krishna Store sellers',
    'PERCENT',
    10.00,
    NULL,
    0,
    CURRENT_DATE,
    NULL,
    'ACTIVE',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM billing_promo_codes WHERE upper(code) = 'KRISHNA10'
);

UPDATE billing_promo_codes
SET
    description = '10% off Catalog Studio Pro for Krishna Store sellers',
    discount_type = 'PERCENT',
    discount_value = 10.00,
    status = 'ACTIVE',
    updated_at = NOW()
WHERE upper(code) = 'KRISHNA10';
