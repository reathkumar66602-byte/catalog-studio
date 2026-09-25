INSERT INTO email_templates (slug, name, subject, html_body, text_body, variables_json, enabled)
VALUES
(
    'plan-activated',
    'Plan activated',
    'Your Catalog Studio {{plan}} plan is active',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Your plan is active</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, your <strong>{{plan}}</strong> plan ({{price}}) is active until {{accessUntil}}.</p>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">You can use the seller workspace and Chrome extension for this billing period. Payment reference: {{reference}}.</p>
    <p style="margin:0 0 20px;">
      <a href="{{loginLink}}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:12px 20px;border-radius:8px;font-weight:600;">Open Catalog Studio</a>
    </p>
    <p style="margin:0;color:#64748b;font-size:13px;">This message was sent to {{email}}.</p>
  </div>
</div>
$html$,
    'Hi {{name}}, your {{plan}} plan ({{price}}) is active until {{accessUntil}}. Sign in at {{loginLink}}. Payment reference: {{reference}}.',
    '["name","username","email","appName","plan","price","accessUntil","reference","loginLink"]'::jsonb,
    TRUE
)
ON CONFLICT (slug) DO NOTHING;
