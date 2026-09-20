ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username VARCHAR(80);

UPDATE users
SET username = regexp_replace(split_part(email, '@', 1), '[^A-Za-z0-9._-]', '', 'g') || '_' || id
WHERE username IS NULL OR btrim(username) = '';

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_lower ON users (lower(username));

ALTER TABLE businesses
    ADD COLUMN IF NOT EXISTS address_line1 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address_line2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS city VARCHAR(120),
    ADD COLUMN IF NOT EXISTS state VARCHAR(120),
    ADD COLUMN IF NOT EXISTS postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS country VARCHAR(80),
    ADD COLUMN IF NOT EXISTS landmark VARCHAR(160),
    ADD COLUMN IF NOT EXISTS google_place_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS latitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7);

UPDATE businesses SET country = 'India' WHERE country IS NULL;

CREATE TABLE IF NOT EXISTS email_templates (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    slug            VARCHAR(80) NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    html_body       TEXT NOT NULL,
    text_body       TEXT,
    variables_json  JSONB NOT NULL DEFAULT '[]'::jsonb,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS otp_challenges (
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id             BIGINT REFERENCES users (id) ON DELETE CASCADE,
    email               VARCHAR(255) NOT NULL,
    purpose             VARCHAR(40) NOT NULL,
    code_hash           VARCHAR(255) NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    attempts            INTEGER NOT NULL DEFAULT 0,
    max_attempts        INTEGER NOT NULL DEFAULT 5,
    consumed_at         TIMESTAMPTZ,
    last_sent_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    remember_device     BOOLEAN NOT NULL DEFAULT FALSE,
    device_name         VARCHAR(200),
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_otp_purpose CHECK (purpose IN ('REGISTER', 'LOGIN', 'VERIFY_EMAIL'))
);

CREATE INDEX IF NOT EXISTS idx_otp_challenges_email ON otp_challenges (email);
CREATE INDEX IF NOT EXISTS idx_otp_challenges_user ON otp_challenges (user_id);

INSERT INTO email_templates (slug, name, subject, html_body, text_body, variables_json, enabled)
VALUES
(
    'otp-register',
    'Registration OTP',
    'Your Catalog Studio verification code is {{otp}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Verify your email</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, use this one-time password to finish creating your seller account.</p>
    <p style="margin:0 0 20px;font-size:32px;letter-spacing:.28em;font-weight:700;color:#0f766e;">{{otp}}</p>
    <p style="margin:0;color:#64748b;font-size:13px;">This code expires in {{expiresMinutes}} minutes. If you did not request it, you can ignore this email.</p>
  </div>
</div>
$html$,
    'Hi {{name}}, your Catalog Studio registration code is {{otp}}. It expires in {{expiresMinutes}} minutes.',
    '["otp","name","username","email","appName","expiresMinutes"]'::jsonb,
    TRUE
),
(
    'otp-login',
    'Login OTP',
    'Your Catalog Studio login code is {{otp}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Sign-in verification</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, use this one-time password to sign in to Catalog Studio.</p>
    <p style="margin:0 0 20px;font-size:32px;letter-spacing:.28em;font-weight:700;color:#0f766e;">{{otp}}</p>
    <p style="margin:0;color:#64748b;font-size:13px;">This code expires in {{expiresMinutes}} minutes. If you did not try to sign in, reset your password.</p>
  </div>
</div>
$html$,
    'Hi {{name}}, your Catalog Studio login code is {{otp}}. It expires in {{expiresMinutes}} minutes.',
    '["otp","name","username","email","appName","expiresMinutes"]'::jsonb,
    TRUE
),
(
    'otp-verify-email',
    'Email verification OTP',
    'Verify your Catalog Studio email with {{otp}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Confirm this email</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, enter this code to verify {{email}}.</p>
    <p style="margin:0 0 20px;font-size:32px;letter-spacing:.28em;font-weight:700;color:#0f766e;">{{otp}}</p>
    <p style="margin:0;color:#64748b;font-size:13px;">This code expires in {{expiresMinutes}} minutes.</p>
  </div>
</div>
$html$,
    'Hi {{name}}, your Catalog Studio email verification code is {{otp}}. It expires in {{expiresMinutes}} minutes.',
    '["otp","name","username","email","appName","expiresMinutes"]'::jsonb,
    TRUE
)
ON CONFLICT (slug) DO NOTHING;
