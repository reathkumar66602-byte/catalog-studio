-- Enquiry admin replies + campaign prior-send filter (safe additive changes).

ALTER TABLE enquiries ADD COLUMN IF NOT EXISTS reply_body TEXT;
ALTER TABLE enquiries ADD COLUMN IF NOT EXISTS replied_at TIMESTAMPTZ;
ALTER TABLE enquiries ADD COLUMN IF NOT EXISTS replied_by_user_id BIGINT REFERENCES users (id) ON DELETE SET NULL;

ALTER TABLE campaign_runs ADD COLUMN IF NOT EXISTS max_prior_sends INT;

-- Same visual structure as enquiry-ack; admin writes {{replyBody}} only.
INSERT INTO email_templates (slug, name, subject, html_body, text_body, variables_json, enabled)
VALUES
(
    'enquiry-reply',
    'Enquiry reply from support',
    'Re: {{subject}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Reply to your enquiry</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}},</p>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;white-space:pre-wrap;">{{replyBody}}</p>
    <p style="margin:24px 0 8px;color:#64748b;font-size:13px;"><strong>Your original message</strong></p>
    <p style="margin:0 0 8px;color:#64748b;font-size:13px;"><strong>Subject:</strong> {{subject}}</p>
    <p style="margin:0;color:#64748b;font-size:13px;line-height:1.6;white-space:pre-wrap;">{{message}}</p>
  </div>
</div>
$html$,
    'Hi {{name}},

{{replyBody}}

---
Your original message ({{subject}}):
{{message}}',
    '["name","email","phone","storeName","subject","message","replyBody","appName","supportEmail"]'::jsonb,
    TRUE
)
ON CONFLICT (slug) DO NOTHING;
