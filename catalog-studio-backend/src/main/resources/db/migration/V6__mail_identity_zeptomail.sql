-- Mail identity for OTP and enquiry is stored in site_settings and can be changed without a deploy.

ALTER TABLE site_settings
    ADD COLUMN IF NOT EXISTS mail_from_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS mail_from_name VARCHAR(120);

UPDATE site_settings
SET
    support_email = 'support@catalogstudio.in',
    mail_from_email = COALESCE(NULLIF(btrim(mail_from_email), ''), 'support@catalogstudio.in'),
    mail_from_name = COALESCE(NULLIF(btrim(mail_from_name), ''), 'Catalog Studio')
WHERE site_key = 'default';

INSERT INTO email_templates (slug, name, subject, html_body, text_body, variables_json, enabled)
VALUES
(
    'enquiry-received',
    'Website enquiry to support',
    'New Catalog Studio enquiry from {{name}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">New website enquiry</h1>
    <p style="margin:0 0 8px;color:#334155;"><strong>Name:</strong> {{name}}</p>
    <p style="margin:0 0 8px;color:#334155;"><strong>Email:</strong> {{email}}</p>
    <p style="margin:0 0 8px;color:#334155;"><strong>Phone:</strong> {{phone}}</p>
    <p style="margin:0 0 8px;color:#334155;"><strong>Store:</strong> {{storeName}}</p>
    <p style="margin:0 0 16px;color:#334155;"><strong>Subject:</strong> {{subject}}</p>
    <p style="margin:0;color:#334155;line-height:1.6;white-space:pre-wrap;">{{message}}</p>
  </div>
</div>
$html$,
    'New enquiry from {{name}} ({{email}}). Store: {{storeName}}. Subject: {{subject}}. Message: {{message}}',
    '["name","email","phone","storeName","subject","message","appName"]'::jsonb,
    TRUE
)
ON CONFLICT (slug) DO NOTHING;
