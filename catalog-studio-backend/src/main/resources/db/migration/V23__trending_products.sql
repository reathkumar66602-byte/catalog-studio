-- Last 10 trending listings a seller opened, plus a shared feed so the
-- marketplace is not called again for a page this server already fetched.

CREATE TABLE user_trending_products (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    marketplace     VARCHAR(16) NOT NULL,
    category_key    VARCHAR(64) NOT NULL,
    page_index      INT NOT NULL,
    slot            INT NOT NULL,
    external_id     VARCHAR(128) NOT NULL,
    title           VARCHAR(500) NOT NULL,
    brand           VARCHAR(160),
    price_label     VARCHAR(64),
    mrp_label       VARCHAR(64),
    rating          VARCHAR(32),
    review_count    VARCHAR(32),
    image_url       TEXT,
    product_url     TEXT NOT NULL,
    seen_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_trending_slot UNIQUE (user_id, marketplace, category_key, slot),
    CONSTRAINT chk_user_trending_marketplace CHECK (marketplace IN ('FLIPKART', 'MEESHO', 'AMAZON')),
    CONSTRAINT chk_user_trending_slot CHECK (slot >= 0 AND slot < 10)
);

CREATE INDEX idx_user_trending_lookup
    ON user_trending_products (user_id, marketplace, category_key);

CREATE TABLE trending_feed_items (
    id              BIGSERIAL PRIMARY KEY,
    marketplace     VARCHAR(16) NOT NULL,
    category_key    VARCHAR(64) NOT NULL,
    rank_index      INT NOT NULL,
    external_id     VARCHAR(128) NOT NULL,
    title           VARCHAR(500) NOT NULL,
    brand           VARCHAR(160),
    price_label     VARCHAR(64),
    mrp_label       VARCHAR(64),
    rating          VARCHAR(32),
    review_count    VARCHAR(32),
    image_url       TEXT,
    product_url     TEXT NOT NULL,
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_trending_feed_rank UNIQUE (marketplace, category_key, rank_index),
    CONSTRAINT chk_trending_feed_marketplace CHECK (marketplace IN ('FLIPKART', 'MEESHO', 'AMAZON'))
);

CREATE INDEX idx_trending_feed_lookup
    ON trending_feed_items (marketplace, category_key, rank_index);

CREATE TABLE trending_feed_state (
    id              BIGSERIAL PRIMARY KEY,
    marketplace     VARCHAR(16) NOT NULL,
    category_key    VARCHAR(64) NOT NULL,
    next_cursor     TEXT,
    next_page       INT NOT NULL DEFAULT 1,
    item_count      INT NOT NULL DEFAULT 0,
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_trending_feed_state UNIQUE (marketplace, category_key),
    CONSTRAINT chk_trending_feed_state_marketplace CHECK (marketplace IN ('FLIPKART', 'MEESHO', 'AMAZON'))
);
