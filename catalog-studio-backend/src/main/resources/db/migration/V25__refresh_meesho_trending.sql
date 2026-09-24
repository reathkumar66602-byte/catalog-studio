-- Meesho cards saved before photo extraction had missing or repeated images.
DELETE FROM user_trending_products WHERE marketplace = 'MEESHO';
DELETE FROM trending_feed_items WHERE marketplace = 'MEESHO';
DELETE FROM trending_feed_state WHERE marketplace = 'MEESHO';
