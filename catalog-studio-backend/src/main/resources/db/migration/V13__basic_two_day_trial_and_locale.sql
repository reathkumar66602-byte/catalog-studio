-- First login is a 2-day BASIC trial. Paid and long referral trials are not rewritten.
-- preferred_locale is stored so dashboard and extension can share the seller's language.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS preferred_locale VARCHAR(16) NOT NULL DEFAULT 'en';

UPDATE billing_settings
SET
    trial_days = 2,
    trial_plan = 'BASIC',
    updated_at = NOW()
WHERE settings_key = 'default';

UPDATE subscriptions s
SET
    plan_id = p.id,
    start_date = CURRENT_DATE,
    end_date = (CURRENT_DATE + INTERVAL '1 day')::date,
    status = 'TRIAL',
    trial_started_at = COALESCE(s.trial_started_at, NOW()),
    updated_at = NOW()
FROM subscription_plans p
WHERE p.name = 'BASIC'
  AND s.status = 'TRIAL'
  AND s.pending_plan_id IS NULL
  AND s.plan_id IN (SELECT id FROM subscription_plans WHERE name = 'PRO')
  AND (s.end_date IS NULL OR s.start_date IS NULL OR (s.end_date - s.start_date) <= 1);
