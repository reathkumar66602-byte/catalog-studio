-- Support ticket email templates (platform-api sends mail; support-service publishes events).
INSERT INTO email_templates (slug, name, subject, html_body, text_body, variables_json, enabled)
VALUES
(
    'support-ticket-created',
    'Support ticket created',
    'We received your support ticket {{ticketNumber}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio Support</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">We received your support ticket</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, thanks for contacting Catalog Studio support.</p>
    <p style="margin:0 0 8px;color:#334155;"><strong>Ticket:</strong> {{ticketNumber}}</p>
    <p style="margin:0 0 8px;color:#334155;"><strong>Subject:</strong> {{subject}}</p>
    <p style="margin:0;color:#334155;line-height:1.6;white-space:pre-wrap;">{{description}}</p>
  </div>
</div>
$html$,
    'Hi {{name}}, we received support ticket {{ticketNumber}} about {{subject}}.',
    '["name","ticketNumber","subject","description","status","priority","appName"]'::jsonb,
    TRUE
),
(
    'support-ticket-assigned',
    'Support ticket assigned',
    'Ticket {{ticketNumber}} was assigned',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio Support</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Your ticket was assigned</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, ticket {{ticketNumber}} was assigned to {{assignedTo}}.</p>
    <p style="margin:0;color:#334155;"><strong>Subject:</strong> {{subject}}</p>
  </div>
</div>
$html$,
    'Hi {{name}}, ticket {{ticketNumber}} was assigned to {{assignedTo}}.',
    '["name","ticketNumber","subject","assignedTo","status","appName"]'::jsonb,
    TRUE
),
(
    'support-ticket-admin-reply',
    'Support admin reply',
    'New reply on ticket {{ticketNumber}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio Support</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">New reply on your ticket</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, there is a new reply on {{ticketNumber}}.</p>
    <p style="margin:0;color:#334155;line-height:1.6;white-space:pre-wrap;">{{replyBody}}</p>
  </div>
</div>
$html$,
    'Hi {{name}}, new reply on {{ticketNumber}}: {{replyBody}}',
    '["name","ticketNumber","subject","replyBody","status","appName"]'::jsonb,
    TRUE
),
(
    'support-ticket-customer-reply',
    'Support customer reply (staff notify)',
    'Customer replied on ticket {{ticketNumber}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio Support</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Customer replied</h1>
    <p style="margin:0 0 8px;color:#334155;"><strong>Ticket:</strong> {{ticketNumber}}</p>
    <p style="margin:0;color:#334155;line-height:1.6;white-space:pre-wrap;">{{replyBody}}</p>
  </div>
</div>
$html$,
    'Customer replied on {{ticketNumber}}: {{replyBody}}',
    '["name","ticketNumber","subject","replyBody","status","appName"]'::jsonb,
    TRUE
),
(
    'support-ticket-status-changed',
    'Support ticket status changed',
    'Ticket {{ticketNumber}} status is now {{status}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio Support</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Ticket status updated</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, ticket {{ticketNumber}} is now {{status}}.</p>
    <p style="margin:0;color:#334155;line-height:1.6;">{{message}}</p>
  </div>
</div>
$html$,
    'Hi {{name}}, ticket {{ticketNumber}} is now {{status}}.',
    '["name","ticketNumber","subject","status","message","appName"]'::jsonb,
    TRUE
),
(
    'support-ticket-resolved',
    'Support ticket resolved',
    'Ticket {{ticketNumber}} was resolved',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio Support</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Ticket resolved</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, ticket {{ticketNumber}} was marked resolved.</p>
    <p style="margin:0;color:#334155;line-height:1.6;white-space:pre-wrap;">{{resolutionSummary}}</p>
  </div>
</div>
$html$,
    'Hi {{name}}, ticket {{ticketNumber}} was resolved. {{resolutionSummary}}',
    '["name","ticketNumber","subject","resolutionSummary","status","appName"]'::jsonb,
    TRUE
),
(
    'support-ticket-closed',
    'Support ticket closed',
    'Ticket {{ticketNumber}} was closed',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio Support</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Ticket closed</h1>
    <p style="margin:0 0 16px;color:#334155;line-height:1.6;">Hi {{name}}, ticket {{ticketNumber}} was closed.</p>
    <p style="margin:0;color:#334155;line-height:1.6;white-space:pre-wrap;">{{resolutionSummary}}</p>
  </div>
</div>
$html$,
    'Hi {{name}}, ticket {{ticketNumber}} was closed. {{resolutionSummary}}',
    '["name","ticketNumber","subject","resolutionSummary","status","appName"]'::jsonb,
    TRUE
),
(
    'support-ticket-reopened',
    'Support ticket reopened',
    'Ticket {{ticketNumber}} was reopened',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio Support</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Ticket reopened</h1>
    <p style="margin:0;color:#334155;line-height:1.6;">Hi {{name}}, ticket {{ticketNumber}} was reopened and is with our support team again.</p>
  </div>
</div>
$html$,
    'Hi {{name}}, ticket {{ticketNumber}} was reopened.',
    '["name","ticketNumber","subject","status","appName"]'::jsonb,
    TRUE
),
(
    'support-ticket-sla-warn',
    'Support SLA warning',
    'SLA warning for ticket {{ticketNumber}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio Support</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">SLA warning</h1>
    <p style="margin:0;color:#334155;line-height:1.6;">Ticket {{ticketNumber}} is approaching its first-response SLA deadline.</p>
  </div>
</div>
$html$,
    'Ticket {{ticketNumber}} is approaching its SLA deadline.',
    '["name","ticketNumber","subject","status","appName"]'::jsonb,
    TRUE
),
(
    'support-ticket-sla-breach',
    'Support SLA breach',
    'SLA breached for ticket {{ticketNumber}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio Support</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">SLA breached</h1>
    <p style="margin:0;color:#334155;line-height:1.6;">Ticket {{ticketNumber}} has breached its first-response SLA.</p>
  </div>
</div>
$html$,
    'Ticket {{ticketNumber}} has breached its SLA.',
    '["name","ticketNumber","subject","status","appName"]'::jsonb,
    TRUE
),
(
    'support-ticket-update',
    'Support ticket generic update',
    'Update on support ticket {{ticketNumber}}',
    $html$
<div style="font-family:Segoe UI,Arial,sans-serif;background:#f8fafc;padding:24px;">
  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;padding:28px;">
    <p style="margin:0 0 8px;color:#0f766e;font-size:12px;letter-spacing:.16em;text-transform:uppercase;">Catalog Studio Support</p>
    <h1 style="margin:0 0 16px;font-size:22px;color:#0f172a;">Support update</h1>
    <p style="margin:0 0 8px;color:#334155;"><strong>Ticket:</strong> {{ticketNumber}}</p>
    <p style="margin:0;color:#334155;line-height:1.6;">{{message}}</p>
  </div>
</div>
$html$,
    'Update on ticket {{ticketNumber}}: {{message}}',
    '["name","ticketNumber","subject","message","status","appName"]'::jsonb,
    TRUE
)
ON CONFLICT (slug) DO NOTHING;
