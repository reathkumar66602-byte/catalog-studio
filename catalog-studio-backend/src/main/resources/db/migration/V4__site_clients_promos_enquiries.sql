-- Public website branding, featured client, store promo codes, and enquiry inbox.

CREATE TABLE site_settings (
    id                      BIGSERIAL PRIMARY KEY,
    site_key                VARCHAR(40) NOT NULL UNIQUE DEFAULT 'default',
    site_name               VARCHAR(120) NOT NULL,
    tagline                 VARCHAR(255),
    hero_title              TEXT NOT NULL,
    hero_subtitle           TEXT,
    logo_url                VARCHAR(500),
    primary_color           VARCHAR(20),
    accent_color            VARCHAR(20),
    hero_background         VARCHAR(20),
    footer_text             TEXT,
    support_email           VARCHAR(255) NOT NULL,
    support_phone           VARCHAR(40),
    enquiry_enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    enquiry_intro           TEXT,
    enquiry_success_message TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE clients (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    store_name      VARCHAR(200) NOT NULL,
    slug            VARCHAR(80) NOT NULL UNIQUE,
    owner_name      VARCHAR(120),
    email           VARCHAR(255),
    phone           VARCHAR(40),
    address         TEXT,
    website_url     VARCHAR(500),
    logo_url        VARCHAR(500),
    tagline         VARCHAR(255),
    about           TEXT,
    branding_json   JSONB NOT NULL DEFAULT '{}'::jsonb,
    featured        BOOLEAN NOT NULL DEFAULT FALSE,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_clients_status CHECK (status IN ('ACTIVE', 'HIDDEN'))
);

CREATE INDEX idx_clients_featured ON clients (featured, status);

CREATE TABLE client_promo_codes (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    client_id       BIGINT NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    code            VARCHAR(40) NOT NULL UNIQUE,
    headline        VARCHAR(160),
    description     VARCHAR(255),
    discount_type   VARCHAR(20) NOT NULL,
    discount_value  NUMERIC(10, 2) NOT NULL,
    trial_days      INTEGER NOT NULL DEFAULT 0,
    valid_from      DATE,
    valid_until     DATE,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_client_promo_discount CHECK (discount_type IN ('PERCENT', 'FIXED', 'TRIAL')),
    CONSTRAINT chk_client_promo_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_client_promo_client ON client_promo_codes (client_id, status);

CREATE TABLE enquiries (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name            VARCHAR(120) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    phone           VARCHAR(40),
    store_name      VARCHAR(200),
    subject         VARCHAR(200),
    message         TEXT NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'NEW',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_enquiries_status CHECK (status IN ('NEW', 'READ', 'REPLIED', 'ARCHIVED'))
);

CREATE INDEX idx_enquiries_created ON enquiries (created_at DESC);
CREATE INDEX idx_enquiries_status ON enquiries (status);

INSERT INTO site_settings (
    site_key,
    site_name,
    tagline,
    hero_title,
    hero_subtitle,
    logo_url,
    primary_color,
    accent_color,
    hero_background,
    footer_text,
    support_email,
    support_phone,
    enquiry_enabled,
    enquiry_intro,
    enquiry_success_message
) VALUES (
    'default',
    'Catalog Studio',
    'Seller tools for listings, labels, and growth',
    'Elevate Your Online Business With Intelligent Image Editing Tools!',
    $$Are you tired of the hassle of manually adjusting the size of your Flipkart shipping labels? Say goodbye to the tedious and time-consuming process! With our one-click auto-crop tool you can resize Flipkart and Meesho labels in a flash, merge PDFs, estimate profit with the calculator, and connect the Catalog Studio Chrome extension. Save valuable time and effort.$$,
    '/logo.svg',
    '#0f766e',
    '#38bdf8',
    '#07111f',
    'Catalog Studio helps sellers crop shipping labels, estimate profit, and autofill marketplace listings. You always submit the listing yourself.',
    'support@catalogstudio.local',
    '+91 98765 43210',
    TRUE,
    'Ask Catalog Studio or Krishna Store about label crop, the profit calculator, or the Chrome extension. We reply from the support email configured in the database.',
    'Thanks. We received your enquiry and will reply from the support email on file.'
);

INSERT INTO clients (
    store_name,
    slug,
    owner_name,
    email,
    phone,
    address,
    website_url,
    logo_url,
    tagline,
    about,
    branding_json,
    featured,
    status
) VALUES (
    'Krishna Store',
    'krishna-store',
    'Krishna',
    'krishna.store@catalogstudio.local',
    '+91 90000 11111',
    'India',
    NULL,
    '/logo.svg',
    'Marketplace-ready seller workspace',
    'Krishna Store uses Catalog Studio to crop Flipkart and Meesho shipping labels, check margins in the calculator, and autofill listings with the Chrome extension.',
    '{"primaryColor":"#0f766e","accentColor":"#f59e0b","heroBackground":"#07111f"}'::jsonb,
    TRUE,
    'ACTIVE'
);

INSERT INTO client_promo_codes (
    client_id,
    code,
    headline,
    description,
    discount_type,
    discount_value,
    trial_days,
    valid_from,
    valid_until,
    status
) VALUES (
    (SELECT id FROM clients WHERE slug = 'krishna-store'),
    'KRISHNA10',
    'Krishna Store seller offer',
    '10% off Catalog Studio Pro for Krishna Store sellers',
    'PERCENT',
    10,
    0,
    DATE '2026-01-01',
    DATE '2027-12-31',
    'ACTIVE'
);
