INSERT INTO email_templates (slug, name, subject, html_body, text_body, variables_json, enabled)
VALUES
(
    'password-reset',
    'Password reset',
    'Reset your Catalog Studio password',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Reset your password</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, we received a request to reset the password for {{email}}.</p>
    <p style="margin:0 0 20px;">
      <a href="{{resetLink}}" style="display:inline-block;background:#0f766e;color:#ffffff;text-decoration:none;padding:12px 20px;border-radius:8px;font-weight:600;">Reset password</a>
    </p>
    <p style="margin:0 0 12px;color:#64748b;font-size:13px;line-height:1.6;word-break:break-all;">If the button does not work, copy this link into your browser:<br>{{resetLink}}</p>
    <p style="margin:0;color:#64748b;font-size:13px;">This link expires in {{expiresMinutes}} minutes. If you did not request it, you can ignore this email.</p>
  </div>
</div>
$html$,
    'Hi {{name}}, reset your Catalog Studio password: {{resetLink}} This link expires in {{expiresMinutes}} minutes. If you did not request it, you can ignore this email.',
    '["name","username","email","appName","resetLink","expiresMinutes"]'::jsonb,
    TRUE
)
ON CONFLICT (slug) DO NOTHING;
