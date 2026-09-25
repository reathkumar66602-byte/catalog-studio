-- Meesho cards stored before product-photo preference and dedupe should refetch.
DELETE FROM user_trending_products WHERE marketplace = 'MEESHO';
DELETE FROM trending_feed_items WHERE marketplace = 'MEESHO';
DELETE FROM trending_feed_state WHERE marketplace = 'MEESHO';
