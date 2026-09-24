-- Paid plans: ₹49 / ₹199 / ₹299 / ₹499 / ₹999.
-- monthlyAiAnalyses = autofill, monthlyShootPhotos = generated shoot photos,
-- monthlyTrendingProducts = product cards, labels stay unlimited.

UPDATE subscription_plans
SET
    price = 49,
    features_json = COALESCE(features_json, '{}'::jsonb) || '{
      "monthlyAiAnalyses": 6,
      "monthlyShootPhotos": 3,
      "monthlyTrendingProducts": 80,
      "labelCrop": "Unlimited label crop"
    }'::jsonb
WHERE name = 'BASIC';

INSERT INTO subscription_plans (name, price, billing_cycle, features_json, status)
VALUES (
    'PLUS',
    199,
    'MONTHLY',
    '{
      "monthlyAiAnalyses": 40,
      "monthlyShootPhotos": 8,
      "monthlyTrendingProducts": 200,
      "products": 200,
      "extensionDevices": 1,
      "teamMembers": 1,
      "marketplaces": ["MEESHO", "AMAZON", "FLIPKART"],
      "labelTools": ["FLIPKART", "MEESHO"],
      "labelCrop": "Unlimited label crop"
    }'::jsonb,
    'ACTIVE'
)
ON CONFLICT (name) DO UPDATE
SET
    price = EXCLUDED.price,
    billing_cycle = EXCLUDED.billing_cycle,
    features_json = COALESCE(subscription_plans.features_json, '{}'::jsonb) || EXCLUDED.features_json,
    status = 'ACTIVE';

UPDATE subscription_plans
SET
    price = 299,
    features_json = COALESCE(features_json, '{}'::jsonb) || '{
      "monthlyAiAnalyses": 12,
      "monthlyShootPhotos": 26,
      "monthlyTrendingProducts": 400,
      "labelCrop": "Unlimited label crop"
    }'::jsonb
WHERE name = 'STARTER';

UPDATE subscription_plans
SET
    price = 499,
    features_json = COALESCE(features_json, '{}'::jsonb) || '{
      "monthlyAiAnalyses": 36,
      "monthlyShootPhotos": 42,
      "monthlyTrendingProducts": 1500,
      "labelCrop": "Unlimited label crop"
    }'::jsonb
WHERE name = 'PRO';

UPDATE subscription_plans
SET
    price = 999,
    features_json = COALESCE(features_json, '{}'::jsonb) || '{
      "monthlyAiAnalyses": 100,
      "monthlyShootPhotos": 82,
      "monthlyTrendingProducts": 4000,
      "labelCrop": "Unlimited label crop"
    }'::jsonb
WHERE name = 'BUSINESS';
