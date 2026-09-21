-- After you confirm the WhatsApp payment screenshot, change ONLY these two values:
--   email     = registered email from the WhatsApp message
--   plan_name = BASIC (₹49) | STARTER (₹99) | PRO (₹499) | BUSINESS (₹999)
--
-- Tables:
--   users                seller account (match by email)
--   subscription_plans   BASIC / STARTER / PRO / BUSINESS
--   subscriptions        the row that actually grants access (status + plan_id + dates)
--   payment_transactions payment log (ACTIVATION / SUCCESS)

BEGIN;

CREATE TEMP TABLE _activate (email text, plan_name text) ON COMMIT DROP;
INSERT INTO _activate VALUES ('user@example.com', 'BASIC');

UPDATE subscriptions sub
SET
    plan_id          = p.id,
    pending_plan_id  = NULL,
    status           = 'ACTIVE',
    start_date       = CURRENT_DATE,
    end_date         = (CURRENT_DATE + INTERVAL '1 month')::date,
    trial_started_at = COALESCE(sub.trial_started_at, NOW()),
    updated_at       = NOW()
FROM _activate a
JOIN users u ON lower(u.email) = lower(a.email)
JOIN subscription_plans p ON p.name = a.plan_name
WHERE sub.user_id = u.id
  AND sub.id = (
      SELECT s2.id
      FROM subscriptions s2
      WHERE s2.user_id = u.id
      ORDER BY s2.created_at DESC
      LIMIT 1
  );

INSERT INTO payment_transactions (
    uuid, user_id, plan_id, plan_name, amount, currency, provider, reference,
    type, status, billing_cycle, notes, created_at, updated_at
)
SELECT
    gen_random_uuid(),
    u.id,
    p.id,
    p.name,
    p.price,
    'INR',
    'MANUAL',
    'whatsapp:' || u.email,
    'ACTIVATION',
    'SUCCESS',
    p.billing_cycle,
    'Activated after WhatsApp screenshot confirmation',
    NOW(),
    NOW()
FROM _activate a
JOIN users u ON lower(u.email) = lower(a.email)
JOIN subscription_plans p ON p.name = a.plan_name;

SELECT
    u.email,
    p.name AS plan,
    p.price,
    s.status,
    s.start_date,
    s.end_date,
    s.updated_at
FROM _activate a
JOIN users u ON lower(u.email) = lower(a.email)
JOIN subscriptions s ON s.user_id = u.id
JOIN subscription_plans p ON p.id = s.plan_id
ORDER BY s.created_at DESC
LIMIT 1;

COMMIT;
