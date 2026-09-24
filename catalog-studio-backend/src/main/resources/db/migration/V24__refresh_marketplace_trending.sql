-- Subcategory searches were the child name only, so Men → Kurtas was stored as a
-- women's "Kurtas" page. Drop Flipkart and Amazon copies so the next visit refetches.
-- Meesho rows stay; that marketplace is on a separate fix.

DELETE FROM user_trending_products WHERE marketplace IN ('FLIPKART', 'AMAZON');
DELETE FROM trending_feed_items WHERE marketplace IN ('FLIPKART', 'AMAZON');
DELETE FROM trending_feed_state WHERE marketplace IN ('FLIPKART', 'AMAZON');
