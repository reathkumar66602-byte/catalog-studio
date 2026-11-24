-- Replace Master branding with PRO+ / BUSINESS.

UPDATE subscription_plans
SET
    name = 'PRO_PLUS',
    features_json = jsonb_set(
        jsonb_set(features_json, '{displayName}', '"PRO+"'::jsonb, true),
        '{group}', '"pro_plus"'::jsonb, true
    )
WHERE name = 'MASTER';

UPDATE subscription_plans
SET
    name = 'BUSINESS_HALF_YEARLY',
    features_json = jsonb_set(
        jsonb_set(features_json, '{displayName}', '"BUSINESS"'::jsonb, true),
        '{group}', '"business_term"'::jsonb, true
    )
WHERE name = 'MASTER_HALF_YEARLY';

UPDATE subscription_plans
SET
    name = 'BUSINESS_YEARLY',
    features_json = jsonb_set(
        jsonb_set(
            jsonb_set(features_json, '{displayName}', '"BUSINESS"'::jsonb, true),
            '{group}', '"business_term"'::jsonb, true
        ),
        '{badge}', '"Best value"'::jsonb, true
    )
WHERE name = 'MASTER_YEARLY';
