-- Keep a single monthly tutorial plan; add a shoot-focused ₹349 plan.

-- Retire multi-term tutorial SKUs (keep rows for historical subscriptions / invoices).
UPDATE subscription_plans
SET status = 'INACTIVE'
WHERE name IN ('TUTORIAL_QUARTERLY', 'TUTORIAL_HALF_YEARLY', 'TUTORIAL_YEARLY');

INSERT INTO subscription_plans (name, price, billing_cycle, features_json, status)
VALUES (
    'TUTORIAL_MONTHLY',
    199.00,
    'MONTHLY',
    '{
      "displayName": "Tutorials",
      "group": "tutorial",
      "termMonths": 1,
      "ecommerceTutorials": "Ecommerce tutorial videos",
      "ecommerceTips": "Ecommerce tutorial videos"
    }'::jsonb,
    'ACTIVE'
)
ON CONFLICT (name) DO UPDATE SET
    price = EXCLUDED.price,
    billing_cycle = EXCLUDED.billing_cycle,
    status = 'ACTIVE',
    features_json = COALESCE(subscription_plans.features_json, '{}'::jsonb) || EXCLUDED.features_json;

-- Move open tutorial subscriptions onto the monthly SKU so access keeps working.
UPDATE subscriptions s
SET plan_id = (SELECT id FROM subscription_plans WHERE name = 'TUTORIAL_MONTHLY' LIMIT 1)
WHERE s.plan_id IN (
    SELECT id FROM subscription_plans
    WHERE name IN ('TUTORIAL_QUARTERLY', 'TUTORIAL_HALF_YEARLY', 'TUTORIAL_YEARLY')
)
AND s.status IN ('ACTIVE', 'TRIAL', 'PAYMENT_PENDING');

-- Shoot-focused plan: high catalog photo quota, minimal AI/trending (server-cost aware).
INSERT INTO subscription_plans (name, price, billing_cycle, features_json, status)
VALUES (
    'SHOOT',
    349.00,
    'MONTHLY',
    '{
      "displayName": "Photo Shoot",
      "group": "seller",
      "termMonths": 1,
      "badge": "Shoot focused",
      "monthlyAiAnalyses": 3,
      "monthlyShootPhotos": 60,
      "monthlyTrendingProducts": 25,
      "products": 50,
      "extensionDevices": 1,
      "teamMembers": 0,
      "labelCrop": "Unlimited label crop",
      "labelTools": ["FLIPKART", "MEESHO"]
    }'::jsonb,
    'ACTIVE'
)
ON CONFLICT (name) DO UPDATE SET
    price = EXCLUDED.price,
    billing_cycle = EXCLUDED.billing_cycle,
    status = 'ACTIVE',
    features_json = COALESCE(subscription_plans.features_json, '{}'::jsonb) || EXCLUDED.features_json;
