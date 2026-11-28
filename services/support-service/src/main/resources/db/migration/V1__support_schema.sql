-- Support-service owned schema. No FK to platform users table.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE support_ticket_categories (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code            VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    description     TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order      INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_support_categories_active_sort ON support_ticket_categories (active, sort_order);

INSERT INTO support_ticket_categories (code, name, description, sort_order) VALUES
    ('ACCOUNT', 'Account & login', 'Sign-in, profile, locale, and workspace access', 10),
    ('PRODUCTS', 'Products & catalog', 'Product CRUD, variants, images, and labels', 20),
    ('MARKETPLACE', 'Marketplaces', 'Meesho, Amazon, Flipkart listings and sync', 30),
    ('ORDERS', 'Orders & OMS', 'Order import, fulfilment, and courier labels', 40),
    ('BILLING', 'Billing & subscriptions', 'Plans, trials, payments, and invoices', 50),
    ('TECHNICAL', 'Technical issues', 'Extension, shoot, uploads, and errors', 60),
    ('OTHER', 'Other', 'Anything that does not fit the categories above', 70);

CREATE TABLE support_sla_configurations (
    id                              BIGSERIAL PRIMARY KEY,
    uuid                            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name                            VARCHAR(160) NOT NULL,
    timezone                        VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    working_days                    VARCHAR(64) NOT NULL DEFAULT 'MON-FRI',
    work_start                      TIME NOT NULL DEFAULT '09:00',
    work_end                        TIME NOT NULL DEFAULT '18:00',
    initial_response_business_days  INT NOT NULL DEFAULT 1,
    warn_hours_before               INT NOT NULL DEFAULT 4,
    active                          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_support_sla_active ON support_sla_configurations (active);

INSERT INTO support_sla_configurations (
    name, timezone, working_days, work_start, work_end,
    initial_response_business_days, warn_hours_before, active
) VALUES (
    'Default India business hours',
    'Asia/Kolkata',
    'MON-FRI',
    '09:00',
    '18:00',
    1,
    4,
    TRUE
);

CREATE TABLE support_sla_holidays (
    id              BIGSERIAL PRIMARY KEY,
    sla_config_id   BIGINT NOT NULL REFERENCES support_sla_configurations (id) ON DELETE CASCADE,
    holiday_date    DATE NOT NULL,
    name            VARCHAR(160) NOT NULL,
    CONSTRAINT uq_support_sla_holiday UNIQUE (sla_config_id, holiday_date)
);

CREATE INDEX idx_support_sla_holidays_config ON support_sla_holidays (sla_config_id, holiday_date);

CREATE TABLE support_chat_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    customer_user_id    BIGINT NOT NULL,
    locale              VARCHAR(16) NOT NULL DEFAULT 'en',
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_support_chat_status CHECK (status IN ('ACTIVE', 'ESCALATED', 'CLOSED'))
);

CREATE INDEX idx_support_chat_sessions_customer ON support_chat_sessions (customer_user_id, created_at DESC);
CREATE INDEX idx_support_chat_sessions_status ON support_chat_sessions (status);

CREATE TABLE support_chat_messages (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    session_id      BIGINT NOT NULL REFERENCES support_chat_sessions (id) ON DELETE CASCADE,
    role            VARCHAR(32) NOT NULL,
    content         TEXT NOT NULL,
    metadata        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_support_chat_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE INDEX idx_support_chat_messages_session ON support_chat_messages (session_id, created_at);

CREATE TABLE support_tickets (
    id                      BIGSERIAL PRIMARY KEY,
    uuid                    UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    ticket_number           VARCHAR(32) NOT NULL UNIQUE,
    customer_user_id        BIGINT NOT NULL,
    customer_email          VARCHAR(255) NOT NULL,
    customer_name           VARCHAR(160),
    subject                 VARCHAR(255) NOT NULL,
    description             TEXT NOT NULL,
    category_id             BIGINT REFERENCES support_ticket_categories (id),
    priority                VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
    status                  VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    locale                  VARCHAR(16) NOT NULL DEFAULT 'en',
    assigned_admin_user_id  BIGINT,
    assigned_admin_name     VARCHAR(160),
    chat_session_id         BIGINT REFERENCES support_chat_sessions (id),
    sla_deadline_at         TIMESTAMPTZ,
    sla_warned_at           TIMESTAMPTZ,
    sla_breached_at         TIMESTAMPTZ,
    first_response_at       TIMESTAMPTZ,
    resolved_at             TIMESTAMPTZ,
    closed_at               TIMESTAMPTZ,
    resolution_summary      TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,
    CONSTRAINT chk_support_ticket_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT chk_support_ticket_status CHECK (status IN (
        'OPEN', 'ASSIGNED', 'IN_PROGRESS', 'WAITING_FOR_CUSTOMER', 'RESOLVED', 'CLOSED', 'REOPENED'
    ))
);

CREATE INDEX idx_support_tickets_customer ON support_tickets (customer_user_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_support_tickets_status ON support_tickets (status, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_support_tickets_priority ON support_tickets (priority)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_support_tickets_assignee ON support_tickets (assigned_admin_user_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_support_tickets_sla ON support_tickets (sla_deadline_at)
    WHERE deleted_at IS NULL AND status NOT IN ('RESOLVED', 'CLOSED');
CREATE INDEX idx_support_tickets_category ON support_tickets (category_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_support_tickets_number ON support_tickets (ticket_number);

CREATE TABLE support_ticket_messages (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    ticket_id       BIGINT NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    author_user_id  BIGINT,
    author_name     VARCHAR(160),
    author_role     VARCHAR(32) NOT NULL,
    body            TEXT NOT NULL,
    internal        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_support_msg_role CHECK (author_role IN ('CUSTOMER', 'ADMIN', 'BOT', 'SYSTEM'))
);

CREATE INDEX idx_support_ticket_messages_ticket ON support_ticket_messages (ticket_id, created_at);

CREATE TABLE support_ticket_attachments (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    ticket_id           BIGINT REFERENCES support_tickets (id) ON DELETE CASCADE,
    message_id          BIGINT REFERENCES support_ticket_messages (id) ON DELETE SET NULL,
    stored_name         VARCHAR(255) NOT NULL,
    original_name       VARCHAR(255) NOT NULL,
    content_type        VARCHAR(120) NOT NULL,
    size_bytes          BIGINT NOT NULL,
    checksum            VARCHAR(128),
    created_by_user_id  BIGINT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_support_attachment_size CHECK (size_bytes >= 0)
);

CREATE INDEX idx_support_attachments_ticket ON support_ticket_attachments (ticket_id);
CREATE INDEX idx_support_attachments_uuid ON support_ticket_attachments (uuid);
CREATE INDEX idx_support_attachments_creator ON support_ticket_attachments (created_by_user_id, created_at DESC);

CREATE TABLE support_ticket_activity_logs (
    id              BIGSERIAL PRIMARY KEY,
    ticket_id       BIGINT NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    actor_user_id   BIGINT,
    actor_name      VARCHAR(160),
    action          VARCHAR(64) NOT NULL,
    from_value      VARCHAR(255),
    to_value        VARCHAR(255),
    metadata        TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_support_activity_ticket ON support_ticket_activity_logs (ticket_id, created_at);

CREATE TABLE support_knowledge_articles (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    slug            VARCHAR(180) NOT NULL UNIQUE,
    title           VARCHAR(255) NOT NULL,
    summary         TEXT,
    body            TEXT NOT NULL,
    locale          VARCHAR(16) NOT NULL DEFAULT 'en',
    category        VARCHAR(64),
    tags            VARCHAR(500),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    verified        BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order      INT NOT NULL DEFAULT 0,
    created_by      BIGINT,
    updated_by      BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_support_kb_locale_active ON support_knowledge_articles (locale, active, verified);
CREATE INDEX idx_support_kb_category ON support_knowledge_articles (category);

CREATE TABLE support_email_logs (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    ticket_id           BIGINT REFERENCES support_tickets (id) ON DELETE SET NULL,
    event_type          VARCHAR(64) NOT NULL,
    recipient           VARCHAR(255) NOT NULL,
    template_slug       VARCHAR(120),
    idempotency_key     VARCHAR(120) NOT NULL UNIQUE,
    status              VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    error               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_support_email_logs_ticket ON support_email_logs (ticket_id, created_at DESC);

-- Knowledge base seeds (verified, English) — CatalogStudio real product areas only.
INSERT INTO support_knowledge_articles
    (slug, title, summary, body, locale, category, tags, active, verified, sort_order)
VALUES
(
    'products-catalog-basics',
    'Managing products in CatalogStudio',
    'Create and edit products, variants, images, and label crops.',
    $kb$CatalogStudio lets workspace users manage a product catalog with photos, variants, and inventory quantities.

**What you can do**
- Add products with title, description, price, and images
- Manage variants (size/colour SKUs) and stock
- Use label/crop tools for marketplace-ready images
- Organise products for listing to connected marketplaces

If a product will not save, check required fields, image size limits, and that your plan still has photo/product quota remaining.$kb$,
    'en', 'PRODUCTS', 'products,catalog,variants,images', TRUE, TRUE, 10
),
(
    'product-shoot-guide',
    'Product shoot and AI image tools',
    'How CatalogStudio shoot / photo tools work and what they do not do.',
    $kb$CatalogStudio includes product shoot and AI-assisted image workflows so sellers can prepare listing photos.

**Good to know**
- Shoot / AI image features consume plan photo quotas
- Supported image types are typically JPEG, PNG, and WebP
- Results depend on source image quality and plan entitlements

The assistant cannot start a shoot or change quota for you — open Products / Shoot in the dashboard, or create a support ticket if a generation fails with an error.$kb$,
    'en', 'TECHNICAL', 'shoot,ai,photos,quota', TRUE, TRUE, 20
),
(
    'marketplace-meesho',
    'Connecting and listing on Meesho',
    'Meesho supplier panel OMS sync, stock, and order import.',
    $kb$CatalogStudio supports Meesho via the supplier panel OMS path (credentials configured by the platform, not pasted in chat).

**Typical flows**
- Connect Meesho so CatalogStudio can sync listings and stock
- Import orders from Meesho into the OMS board
- Push stock changes when inventory updates in CatalogStudio

Never share Meesho passwords in chat. If sync fails, note the exact error time and create a ticket under Marketplaces.$kb$,
    'en', 'MARKETPLACE', 'meesho,oms,listings,stock', TRUE, TRUE, 30
),
(
    'marketplace-amazon-flipkart',
    'Amazon and Flipkart marketplace connections',
    'SP-API / Flipkart partner app connections and listing sync.',
    $kb$CatalogStudio can connect Amazon (SP-API) and Flipkart (API Partner) for listing and order workflows when those OMS integrations are enabled for your workspace.

**Important**
- OAuth / partner credentials are configured by the platform team
- Seller self-access keys are not the same as partner API apps
- Enablement flags control whether Amazon or Flipkart OMS is live

For connection errors, include the marketplace name and approximate time in a ticket — do not paste client secrets into chat.$kb$,
    'en', 'MARKETPLACE', 'amazon,flipkart,oms,oauth', TRUE, TRUE, 40
),
(
    'wholesale-supplier-retailer',
    'Wholesale supplier and retailer marketplace',
    'Live Stock wholesale stores, GST verification, and buyer orders.',
    $kb$The wholesale (Live Stock) module lets verified suppliers publish wholesale products and retailers place orders.

**Supplier side**
- Create a wholesale store with GSTIN verification
- Publish products with wholesale pricing
- Accept / reject / fulfil retailer orders and generate GST bills

**Retailer / buyer side**
- Browse wholesale products and place orders
- Confirm goods receipts where that flow is available

Wholesale data lives in the supplier microservice; account login still uses CatalogStudio auth.$kb$,
    'en', 'PRODUCTS', 'wholesale,supplier,retailer,gst', TRUE, TRUE, 50
),
(
    'orders-oms-labels',
    'Orders, OMS board, and shipping labels',
    'How imported marketplace orders and courier labels work.',
    $kb$The OMS board collects marketplace orders (for example Meesho) so you can fulfil them from CatalogStudio.

**Common steps**
- Import or sync orders from a connected marketplace
- Update fulfilment status and courier information
- Generate or download shipping labels when a courier integration is configured (for example Shiprocket / Delhivery where enabled)

If a label fails, check courier credentials and the order’s marketplace status, then open an Orders ticket with the order number.$kb$,
    'en', 'ORDERS', 'orders,oms,labels,courier', TRUE, TRUE, 60
),
(
    'subscriptions-billing',
    'Plans, trials, and billing',
    'Subscription plans, photo quotas, payments, and invoices.',
    $kb$CatalogStudio access is controlled by subscription plans (including trial periods where offered).

**Billing basics**
- Plans define feature access and quotas (products, photos, shoot, etc.)
- Payments may go through configured gateways (for example Cashfree) or the documented payment flow
- Tax invoices / billing company details appear on plan invoices when configured

The chat assistant cannot change your plan or refund payments. For billing disputes, create a Billing ticket with the payment reference.$kb$,
    'en', 'BILLING', 'subscription,billing,plan,quota,invoice', TRUE, TRUE, 70
),
(
    'inventory-stock',
    'Inventory and stock sync',
    'Workspace inventory, reservations, and marketplace stock push.',
    $kb$Inventory in CatalogStudio tracks on-hand quantities for products and variants.

**Related behaviours**
- Stock can sync to connected marketplaces when those integrations are enabled
- OMS may reserve stock on order import depending on configuration
- Wholesale goods receipts can credit inventory for buyers

If stock numbers look wrong after a sync, note the SKU, marketplace, and last sync time in a Technical or Marketplace ticket.$kb$,
    'en', 'PRODUCTS', 'inventory,stock,sync', TRUE, TRUE, 80
),
(
    'account-login-locale',
    'Account, login, and preferred language',
    'Sign-in, OTP, password reset, and locale preferences.',
    $kb$Workspace accounts sign in with email and password (OTP verification during registration where enabled).

**You can**
- Update profile name and preferred locale from account settings
- Reset a forgotten password via the password-reset email flow
- Prefer languages such as English, Hindi, Bengali, Marathi, Tamil, Kannada, Punjabi, Urdu, Japanese, and Chinese

Support staff use ADMIN / SUPERADMIN roles. Contact Us / public enquiry forms are separate from in-app support tickets.$kb$,
    'en', 'ACCOUNT', 'login,otp,locale,password', TRUE, TRUE, 90
),
(
    'labels-crop-extension',
    'Labels, crop tools, and browser extension',
    'Marketplace label sizing and CatalogStudio extension tips.',
    $kb$CatalogStudio provides label and crop helpers so product images match marketplace requirements, including flows used with the browser extension.

**Tips**
- Use supported image formats and stay within upload size limits
- Re-crop if a marketplace rejects an image for dimensions
- Extension features require you to be signed in to CatalogStudio

For extension bugs, include browser name/version and a short reproduction in a Technical ticket.$kb$,
    'en', 'TECHNICAL', 'labels,crop,extension', TRUE, TRUE, 100
);
