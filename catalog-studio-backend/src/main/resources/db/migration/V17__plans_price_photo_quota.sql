-- Paid plans: ₹49 / ₹99 / ₹499 / ₹999 with OpenAI photo analysis quotas 8 / 20 / 100 / 250.

UPDATE subscription_plans
SET
    price = 49,
    features_json = COALESCE(features_json, '{}'::jsonb) || '{"monthlyAiAnalyses": 8}'::jsonb
WHERE name = 'BASIC';

UPDATE subscription_plans
SET
    price = 99,
    features_json = COALESCE(features_json, '{}'::jsonb) || '{"monthlyAiAnalyses": 20}'::jsonb
WHERE name = 'STARTER';

UPDATE subscription_plans
SET
    price = 499,
    features_json = COALESCE(features_json, '{}'::jsonb) || '{"monthlyAiAnalyses": 100}'::jsonb
WHERE name = 'PRO';

UPDATE subscription_plans
SET
    price = 999,
    features_json = COALESCE(features_json, '{}'::jsonb) || '{"monthlyAiAnalyses": 250}'::jsonb
WHERE name = 'BUSINESS';
