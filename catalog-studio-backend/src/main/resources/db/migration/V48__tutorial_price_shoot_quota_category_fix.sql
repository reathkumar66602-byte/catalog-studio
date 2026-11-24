-- Tutorial monthly → ₹1999; Shoot plan → 100 photos; strip tips from seller plans;
-- move mis-tagged women's products out of Men's Wear.

UPDATE subscription_plans
SET
    price = 1999.00,
    features_json = COALESCE(features_json, '{}'::jsonb) || '{
      "displayName": "Tutorials",
      "group": "tutorial",
      "termMonths": 1,
      "ecommerceTutorials": "Ecommerce tutorial videos"
    }'::jsonb
WHERE name = 'TUTORIAL_MONTHLY';

-- Remove tip/tutorial marketing bullets from non-tutorial plans.
UPDATE subscription_plans
SET features_json = (COALESCE(features_json, '{}'::jsonb) - 'ecommerceTips' - 'ecommerceTutorials')
WHERE name NOT LIKE 'TUTORIAL_%';

UPDATE subscription_plans
SET
    features_json = COALESCE(features_json, '{}'::jsonb) || '{
      "monthlyShootPhotos": 100,
      "monthlyAiAnalyses": 3,
      "monthlyTrendingProducts": 25,
      "badge": "Shoot focused"
    }'::jsonb
WHERE name = 'SHOOT';

-- Data fix: women's garments wrongly filed under Men's Wear.
UPDATE wholesale_products p
SET category_id = c_ladies.id
FROM wholesale_categories c_men, wholesale_categories c_ladies
WHERE p.category_id = c_men.id
  AND c_men.slug = 'mens-wear'
  AND c_ladies.slug = 'ladies-wear'
  AND (
      lower(p.title) LIKE '%women%'
      OR lower(p.title) LIKE '%womens%'
      OR lower(p.title) LIKE '%lady%'
      OR lower(p.title) LIKE '%ladies%'
      OR lower(coalesce(p.product_code, '')) LIKE 'wom%'
  );

UPDATE wholesale_categories c
SET product_count = (
    SELECT count(*)::int
    FROM wholesale_products p
    WHERE p.category_id = c.id AND p.status = 'ACTIVE'
)
WHERE c.slug IN ('mens-wear', 'ladies-wear');
