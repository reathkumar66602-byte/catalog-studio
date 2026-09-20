INSERT INTO email_templates (slug, name, subject, html_body, text_body, variables_json, enabled)
VALUES
(
    'enquiry-ack',
    'Enquiry acknowledgement to visitor',
    'We received your Catalog Studio enquiry',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">We received your enquiry</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, thanks for writing in. Our team will reply from support@catalogstudio.in.</p>
    <p style="margin:0 0 8px;color:#334155;"><strong>Subject:</strong> {{subject}}</p>
    <p style="margin:0;color:#334155;line-height:1.6;white-space:pre-wrap;">{{message}}</p>
  </div>
</div>
$html$,
    'Hi {{name}}, we received your enquiry about {{subject}}. Our team will reply from support@catalogstudio.in.',
    '["name","email","phone","storeName","subject","message","appName"]'::jsonb,
    TRUE
)
ON CONFLICT (slug) DO NOTHING;
