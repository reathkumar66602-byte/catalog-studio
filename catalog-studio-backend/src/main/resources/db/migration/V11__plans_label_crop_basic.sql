-- Add Flipkart + Meesho label crop to every plan, and a ₹50 BASIC starter.

UPDATE subscription_plans
SET features_json = COALESCE(features_json, '{}'::jsonb) || '{
  "labelTools": ["FLIPKART", "MEESHO"],
  "labelCrop": "Ultimate Flipkart and Meesho label crop"
}'::jsonb
WHERE status = 'ACTIVE';

INSERT INTO subscription_plans (name, price, billing_cycle, features_json, status)
VALUES (
    'BASIC',
    50,
    'MONTHLY',
    '{
      "monthlyAiAnalyses": 5,
      "products": 15,
      "extensionDevices": 1,
      "teamMembers": 0,
      "marketplaces": ["MEESHO", "FLIPKART"],
      "labelTools": ["FLIPKART", "MEESHO"],
      "labelCrop": "Ultimate Flipkart and Meesho label crop"
    }'::jsonb,
    'ACTIVE'
)
ON CONFLICT (name) DO UPDATE
SET
    price = EXCLUDED.price,
    billing_cycle = EXCLUDED.billing_cycle,
    features_json = EXCLUDED.features_json,
    status = 'ACTIVE';
