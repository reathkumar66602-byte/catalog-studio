ALTER TABLE extension_devices
    ADD COLUMN IF NOT EXISTS locked_shop_name VARCHAR(191),
    ADD COLUMN IF NOT EXISTS locked_shop_uid VARCHAR(80);

CREATE TABLE IF NOT EXISTS extension_user_settings (
    user_id         BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    settings_json   JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS extension_inventory_photos (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_id       VARCHAR(120) NOT NULL,
    thumb_url       TEXT,
    storage_key     VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, source_id)
);

CREATE INDEX IF NOT EXISTS idx_ext_photos_user ON extension_inventory_photos (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS extension_tickets (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ticket_no       VARCHAR(80),
    draft           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ext_tickets_user ON extension_tickets (user_id, created_at DESC);
