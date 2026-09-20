-- Catalog Studio core schema
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name                VARCHAR(120) NOT NULL,
    email               VARCHAR(255) NOT NULL UNIQUE,
    mobile              VARCHAR(20),
    password_hash       VARCHAR(255) NOT NULL,
    role                VARCHAR(32) NOT NULL DEFAULT 'SELLER',
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    mobile_verified     BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_ai_provider VARCHAR(32),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'SELLER', 'TEAM_MEMBER')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'PENDING'))
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_mobile ON users (mobile);
CREATE INDEX idx_users_role ON users (role);

CREATE TABLE businesses (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    business_name       VARCHAR(200) NOT NULL,
    gst_number          VARCHAR(32),
    address             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE team_memberships (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    owner_user_id       BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    member_user_id      BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role                VARCHAR(32) NOT NULL DEFAULT 'TEAM_MEMBER',
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (owner_user_id, member_user_id)
);

CREATE TABLE user_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    refresh_token_hash  VARCHAR(255) NOT NULL UNIQUE,
    device_name         VARCHAR(200),
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(500),
    trusted_device      BOOLEAN NOT NULL DEFAULT FALSE,
    revoked             BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at          TIMESTAMPTZ NOT NULL,
    last_used_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_sessions_user ON user_sessions (user_id);

CREATE TABLE email_verification_tokens (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash          VARCHAR(255) NOT NULL UNIQUE,
    expires_at          TIMESTAMPTZ NOT NULL,
    used_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE password_reset_tokens (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash          VARCHAR(255) NOT NULL UNIQUE,
    expires_at          TIMESTAMPTZ NOT NULL,
    used_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE products (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    business_id         BIGINT REFERENCES businesses (id) ON DELETE SET NULL,
    name                VARCHAR(255),
    product_type        VARCHAR(120),
    category            VARCHAR(120),
    subcategory         VARCHAR(120),
    gender              VARCHAR(40),
    age_group           VARCHAR(40),
    primary_color       VARCHAR(80),
    pattern             VARCHAR(80),
    material            VARCHAR(80),
    sleeve_type         VARCHAR(80),
    neck_type           VARCHAR(80),
    collar_type         VARCHAR(80),
    fit                 VARCHAR(80),
    occasion            VARCHAR(80),
    style               VARCHAR(80),
    description         TEXT,
    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_products_user ON products (user_id);
CREATE INDEX idx_products_category ON products (category);
CREATE INDEX idx_products_color ON products (primary_color);
CREATE INDEX idx_products_status ON products (status);
CREATE INDEX idx_products_created ON products (created_at DESC);

CREATE TABLE product_images (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    product_id          BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    image_url           VARCHAR(500) NOT NULL,
    storage_key         VARCHAR(255) NOT NULL,
    image_order         INTEGER NOT NULL DEFAULT 0,
    is_primary          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_images_product ON product_images (product_id);

CREATE TABLE product_analysis (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    product_id          BIGINT REFERENCES products (id) ON DELETE SET NULL,
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider            VARCHAR(40) NOT NULL,
    model               VARCHAR(80) NOT NULL,
    request_data        JSONB,
    response_data       JSONB,
    overall_confidence  NUMERIC(5, 4),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_analysis_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_product_analysis_user ON product_analysis (user_id);
CREATE INDEX idx_product_analysis_product ON product_analysis (product_id);
CREATE INDEX idx_product_analysis_created ON product_analysis (created_at DESC);

CREATE TABLE product_attributes (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    product_id          BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    attribute_name      VARCHAR(120) NOT NULL,
    attribute_value     TEXT,
    confidence          NUMERIC(5, 4),
    source              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_attr_source CHECK (source IN ('AI', 'USER', 'MARKETPLACE'))
);

CREATE INDEX idx_product_attributes_product ON product_attributes (product_id);

CREATE TABLE product_titles (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    product_id          BIGINT NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    title               VARCHAR(255) NOT NULL,
    is_selected         BOOLEAN NOT NULL DEFAULT FALSE,
    source              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_title_source CHECK (source IN ('AI', 'USER', 'MARKETPLACE'))
);

CREATE INDEX idx_product_titles_product ON product_titles (product_id);

CREATE TABLE listing_templates (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                VARCHAR(160) NOT NULL,
    marketplace         VARCHAR(40) NOT NULL,
    product_type        VARCHAR(120),
    template_json       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_listing_templates_user ON listing_templates (user_id);

CREATE TABLE autofill_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                VARCHAR(160) NOT NULL,
    marketplace         VARCHAR(40) NOT NULL,
    profile_json        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_autofill_profiles_user ON autofill_profiles (user_id);

CREATE TABLE marketplace_connections (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    marketplace         VARCHAR(40) NOT NULL,
    connection_type     VARCHAR(40) NOT NULL DEFAULT 'EXTENSION',
    status              VARCHAR(32) NOT NULL DEFAULT 'NOT_CONNECTED',
    configuration_json  JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_activity_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, marketplace),
    CONSTRAINT chk_mc_status CHECK (status IN ('CONNECTED', 'NOT_CONNECTED', 'ERROR'))
);

CREATE TABLE marketplace_attribute_mappings (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    marketplace         VARCHAR(40) NOT NULL,
    internal_attribute  VARCHAR(120) NOT NULL,
    marketplace_attribute VARCHAR(160) NOT NULL,
    internal_value      VARCHAR(160) NOT NULL,
    marketplace_value   VARCHAR(160) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mam_marketplace ON marketplace_attribute_mappings (marketplace, internal_attribute);

CREATE TABLE categories (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name                VARCHAR(120) NOT NULL,
    parent_id           BIGINT REFERENCES categories (id) ON DELETE SET NULL,
    gender              VARCHAR(40),
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE extension_devices (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_name         VARCHAR(160) NOT NULL,
    extension_key_hash  VARCHAR(255) NOT NULL UNIQUE,
    last_active_at      TIMESTAMPTZ,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ext_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX idx_extension_devices_user ON extension_devices (user_id);

CREATE TABLE extension_activity_logs (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    marketplace         VARCHAR(40),
    action              VARCHAR(80) NOT NULL,
    product_id          BIGINT REFERENCES products (id) ON DELETE SET NULL,
    details             JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ext_activity_user ON extension_activity_logs (user_id, created_at DESC);

CREATE TABLE subscription_plans (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name                VARCHAR(40) NOT NULL UNIQUE,
    price               NUMERIC(10, 2) NOT NULL DEFAULT 0,
    billing_cycle       VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    features_json       JSONB NOT NULL DEFAULT '{}'::jsonb,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE subscriptions (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    plan_id             BIGINT NOT NULL REFERENCES subscription_plans (id),
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    start_date          DATE NOT NULL,
    end_date            DATE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_sub_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED', 'TRIAL'))
);

CREATE INDEX idx_subscriptions_user ON subscriptions (user_id);

CREATE TABLE referral_codes (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT REFERENCES users (id) ON DELETE SET NULL,
    code                VARCHAR(40) NOT NULL UNIQUE,
    discount_type       VARCHAR(20) NOT NULL,
    discount_value      NUMERIC(10, 2) NOT NULL,
    trial_days          INTEGER NOT NULL DEFAULT 0,
    description         VARCHAR(255),
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ref_discount CHECK (discount_type IN ('PERCENT', 'FIXED', 'TRIAL'))
);

CREATE TABLE referrals (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    referrer_user_id    BIGINT REFERENCES users (id) ON DELETE SET NULL,
    referred_user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    referral_code       VARCHAR(40) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_logs (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT REFERENCES users (id) ON DELETE SET NULL,
    action              VARCHAR(80) NOT NULL,
    entity_type         VARCHAR(80),
    entity_id           VARCHAR(80),
    ip_address          VARCHAR(64),
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_user ON audit_logs (user_id, created_at DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
