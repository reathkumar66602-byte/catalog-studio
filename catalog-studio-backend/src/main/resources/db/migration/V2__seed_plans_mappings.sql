INSERT INTO subscription_plans (name, price, billing_cycle, features_json, status)
VALUES
    ('FREE', 0, 'MONTHLY', '{"monthlyAiAnalyses": 10, "products": 25, "extensionDevices": 1, "teamMembers": 0, "marketplaces": ["MEESHO"]}'::jsonb, 'ACTIVE'),
    ('STARTER', 499, 'MONTHLY', '{"monthlyAiAnalyses": 100, "products": 200, "extensionDevices": 2, "teamMembers": 1, "marketplaces": ["MEESHO", "AMAZON"]}'::jsonb, 'ACTIVE'),
    ('PRO', 1499, 'MONTHLY', '{"monthlyAiAnalyses": 500, "products": 2000, "extensionDevices": 5, "teamMembers": 5, "marketplaces": ["MEESHO", "AMAZON", "FLIPKART"]}'::jsonb, 'ACTIVE'),
    ('BUSINESS', 4999, 'MONTHLY', '{"monthlyAiAnalyses": 5000, "products": 20000, "extensionDevices": 20, "teamMembers": 25, "marketplaces": ["MEESHO", "AMAZON", "FLIPKART"]}'::jsonb, 'ACTIVE');

INSERT INTO referral_codes (code, discount_type, discount_value, trial_days, description, status)
VALUES ('ABC2026', 'PERCENT', 20, 14, 'Launch referral: 20% off first month + 14-day Pro trial', 'ACTIVE');

INSERT INTO marketplace_attribute_mappings (marketplace, internal_attribute, marketplace_attribute, internal_value, marketplace_value)
VALUES
    ('MEESHO', 'primaryColor', 'color', 'Navy Blue', 'Navy'),
    ('MEESHO', 'primaryColor', 'color', 'Black', 'Black'),
    ('MEESHO', 'primaryColor', 'color', 'White', 'White'),
    ('MEESHO', 'primaryColor', 'color', 'Red', 'Red'),
    ('MEESHO', 'pattern', 'pattern', 'Checked', 'Checks'),
    ('MEESHO', 'pattern', 'pattern', 'Solid', 'Solid'),
    ('MEESHO', 'pattern', 'pattern', 'Striped', 'Stripes'),
    ('MEESHO', 'material', 'fabric', 'Cotton', 'Cotton'),
    ('MEESHO', 'material', 'fabric', 'Polyester', 'Polyester'),
    ('MEESHO', 'sleeveType', 'sleeve', 'Full Sleeve', 'Full Sleeves'),
    ('MEESHO', 'sleeveType', 'sleeve', 'Half Sleeve', 'Half Sleeves'),
    ('MEESHO', 'fit', 'fit', 'Regular Fit', 'Regular'),
    ('MEESHO', 'fit', 'fit', 'Slim Fit', 'Slim'),
    ('AMAZON', 'primaryColor', 'color_name', 'Navy Blue', 'Navy'),
    ('AMAZON', 'pattern', 'pattern_type', 'Checked', 'Checkered'),
    ('AMAZON', 'material', 'fabric_type', 'Cotton', 'Cotton'),
    ('FLIPKART', 'primaryColor', 'color', 'Navy Blue', 'Navy Blue'),
    ('FLIPKART', 'pattern', 'pattern', 'Checked', 'Checked'),
    ('FLIPKART', 'material', 'fabric', 'Cotton', 'Cotton');

INSERT INTO categories (name, gender, status)
VALUES
    ('Clothing', NULL, 'ACTIVE'),
    ('Footwear', NULL, 'ACTIVE'),
    ('Accessories', NULL, 'ACTIVE');

INSERT INTO categories (name, parent_id, gender, status)
SELECT 'Men Shirts', id, 'Men', 'ACTIVE' FROM categories WHERE name = 'Clothing';
INSERT INTO categories (name, parent_id, gender, status)
SELECT 'Women Kurtis', id, 'Women', 'ACTIVE' FROM categories WHERE name = 'Clothing';
INSERT INTO categories (name, parent_id, gender, status)
SELECT 'T-Shirts', id, NULL, 'ACTIVE' FROM categories WHERE name = 'Clothing';
