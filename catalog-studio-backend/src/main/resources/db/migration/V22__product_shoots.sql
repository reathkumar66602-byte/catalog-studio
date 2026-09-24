-- Catalog photo shoots generated from garment references.

CREATE TABLE product_shoots (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mode            VARCHAR(16) NOT NULL,
    model_age       VARCHAR(16) NOT NULL,
    marketplace     BOOLEAN NOT NULL DEFAULT FALSE,
    trial_limited   BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(16) NOT NULL,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_product_shoots_mode CHECK (mode IN ('SINGLE', 'COMBO')),
    CONSTRAINT chk_product_shoots_status CHECK (status IN ('PENDING', 'COMPLETED', 'PARTIAL', 'FAILED'))
);

CREATE INDEX idx_product_shoots_user_created ON product_shoots (user_id, created_at DESC);

CREATE TABLE product_shoot_images (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shoot_id        BIGINT NOT NULL REFERENCES product_shoots (id) ON DELETE CASCADE,
    kind            VARCHAR(32) NOT NULL,
    is_generated    BOOLEAN NOT NULL DEFAULT FALSE,
    storage_key     VARCHAR(255) NOT NULL,
    public_url      TEXT NOT NULL,
    content_type    VARCHAR(80) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_shoot_images_shoot ON product_shoot_images (shoot_id, sort_order);
