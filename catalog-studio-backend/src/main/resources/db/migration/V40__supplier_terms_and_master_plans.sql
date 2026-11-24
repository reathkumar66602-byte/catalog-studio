-- Supplier is sold by term, with the yearly price capped at ₹1,199.
-- Half-year is ₹749 (about ₹125/month) and a quarter is ₹449 (about ₹150/month),
-- so a longer term is the cheaper rate for the same store and quotas.
-- Master monthly is unlimited seller tools without a wholesale store.
-- Master half-year and year add the store, priced from AI, photo, and server cost
-- with a longer-term discount.

UPDATE subscription_plans
SET status = 'INACTIVE'
WHERE name = 'SUPPLIER';

INSERT INTO subscription_plans (name, price, billing_cycle, features_json, status)
VALUES
    (
        'SUPPLIER_QUARTERLY',
        449,
        'QUARTERLY',
        '{
          "displayName": "Supplier",
          "group": "supplier",
          "termMonths": 3,
          "monthlyAiAnalyses": 40,
          "monthlyShootPhotos": 8,
          "monthlyTrendingProducts": 200,
          "products": 500,
          "extensionDevices": 2,
          "teamMembers": 1,
          "marketplaces": ["MEESHO", "AMAZON", "FLIPKART"],
          "labelTools": ["FLIPKART", "MEESHO"],
          "labelCrop": "Unlimited label crop",
          "liveStock": true,
          "wholesaleSupplier": true
        }'::jsonb,
        'ACTIVE'
    ),
    (
        'SUPPLIER_HALF_YEARLY',
        749,
        'HALF_YEARLY',
        '{
          "displayName": "Supplier",
          "group": "supplier",
          "termMonths": 6,
          "monthlyAiAnalyses": 40,
          "monthlyShootPhotos": 8,
          "monthlyTrendingProducts": 200,
          "products": 500,
          "extensionDevices": 2,
          "teamMembers": 1,
          "marketplaces": ["MEESHO", "AMAZON", "FLIPKART"],
          "labelTools": ["FLIPKART", "MEESHO"],
          "labelCrop": "Unlimited label crop",
          "liveStock": true,
          "wholesaleSupplier": true
        }'::jsonb,
        'ACTIVE'
    ),
    (
        'SUPPLIER_YEARLY',
        1199,
        'YEARLY',
        '{
          "displayName": "Supplier",
          "group": "supplier",
          "termMonths": 12,
          "badge": "Best value",
          "monthlyAiAnalyses": 40,
          "monthlyShootPhotos": 8,
          "monthlyTrendingProducts": 200,
          "products": 500,
          "extensionDevices": 2,
          "teamMembers": 1,
          "marketplaces": ["MEESHO", "AMAZON", "FLIPKART"],
          "labelTools": ["FLIPKART", "MEESHO"],
          "labelCrop": "Unlimited label crop",
          "liveStock": true,
          "wholesaleSupplier": true
        }'::jsonb,
        'ACTIVE'
    ),
    (
        'MASTER',
        1499,
        'MONTHLY',
        '{
          "displayName": "Master",
          "group": "master",
          "termMonths": 1,
          "unlimitedAccess": true,
          "monthlyAiAnalyses": 400,
          "monthlyShootPhotos": 120,
          "monthlyTrendingProducts": 5000,
          "products": 20000,
          "extensionDevices": 10,
          "teamMembers": 10,
          "marketplaces": ["MEESHO", "AMAZON", "FLIPKART"],
          "labelTools": ["FLIPKART", "MEESHO"],
          "labelCrop": "Unlimited label crop",
          "liveStock": true,
          "wholesaleSupplier": false
        }'::jsonb,
        'ACTIVE'
    ),
    (
        'MASTER_HALF_YEARLY',
        7499,
        'HALF_YEARLY',
        '{
          "displayName": "Master",
          "group": "master",
          "termMonths": 6,
          "unlimitedAccess": true,
          "monthlyAiAnalyses": 400,
          "monthlyShootPhotos": 120,
          "monthlyTrendingProducts": 5000,
          "products": 20000,
          "extensionDevices": 10,
          "teamMembers": 10,
          "marketplaces": ["MEESHO", "AMAZON", "FLIPKART"],
          "labelTools": ["FLIPKART", "MEESHO"],
          "labelCrop": "Unlimited label crop",
          "liveStock": true,
          "wholesaleSupplier": true
        }'::jsonb,
        'ACTIVE'
    ),
    (
        'MASTER_YEARLY',
        12499,
        'YEARLY',
        '{
          "displayName": "Master",
          "group": "master",
          "termMonths": 12,
          "badge": "Best value",
          "unlimitedAccess": true,
          "monthlyAiAnalyses": 400,
          "monthlyShootPhotos": 120,
          "monthlyTrendingProducts": 5000,
          "products": 20000,
          "extensionDevices": 10,
          "teamMembers": 10,
          "marketplaces": ["MEESHO", "AMAZON", "FLIPKART"],
          "labelTools": ["FLIPKART", "MEESHO"],
          "labelCrop": "Unlimited label crop",
          "liveStock": true,
          "wholesaleSupplier": true
        }'::jsonb,
        'ACTIVE'
    )
ON CONFLICT (name) DO UPDATE
SET
    price = EXCLUDED.price,
    billing_cycle = EXCLUDED.billing_cycle,
    features_json = EXCLUDED.features_json,
    status = 'ACTIVE';
