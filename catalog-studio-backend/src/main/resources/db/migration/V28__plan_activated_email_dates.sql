UPDATE email_templates
SET
    subject = 'Your Catalog Studio {{plan}} plan is active',
    html_body = $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Your plan is active</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, your plan is now active.</p>
    <p style="margin:0 0 8px;color:#334155;line-height:1.6;"><strong>Plan:</strong> {{planDetail}}</p>
    <p style="margin:0 0 8px;color:#334155;line-height:1.6;"><strong>Start date:</strong> {{startDate}}</p>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;"><strong>End date:</strong> {{endDate}}</p>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">You can use the seller workspace and Chrome extension for this billing period. Payment reference: {{reference}}.</p>
    <p style="margin:0 0 20px;">
      <a href="{{loginLink}}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:12px 20px;border-radius:8px;font-weight:600;">Open Catalog Studio</a>
    </p>
    <p style="margin:0;color:#64748b;font-size:13px;">This message was sent to {{email}}.</p>
  </div>
</div>
$html$,
    text_body = 'Hi {{name}}, your plan is now active. Plan: {{planDetail}}. Start date: {{startDate}}. End date: {{endDate}}. Sign in at {{loginLink}}. Payment reference: {{reference}}.',
    variables_json = '["name","username","email","appName","plan","price","billingCycle","planDetail","startDate","endDate","accessUntil","reference","loginLink"]'::jsonb
WHERE slug = 'plan-activated';
