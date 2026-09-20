-- Replace the cloned marketing headline with Catalog Studio copy.
-- Only updates the default seed text so admin-edited titles stay intact.

UPDATE site_settings
SET
    hero_title = 'Crop labels, estimate profit, and fill listings faster',
    hero_subtitle = 'Catalog Studio crops Flipkart and Meesho shipping labels in your browser, estimates Meesho margins, and pairs a Chrome extension that fills GST and HSN. You always submit the listing yourself.'
WHERE site_key = 'default'
  AND hero_title = 'Elevate Your Online Business With Intelligent Image Editing Tools!';
