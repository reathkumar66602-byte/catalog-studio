-- Super admin leftover seller plan is display-only; staff access is unlimited.
-- Keep the stored plan on BUSINESS so Dashboard matches the staff account.

UPDATE subscriptions s
SET plan_id = p.id,
    status = 'ACTIVE',
    pending_plan_id = NULL,
    start_date = CURRENT_DATE,
    end_date = CURRENT_DATE + INTERVAL '1 year',
    updated_at = NOW()
FROM users u
CROSS JOIN subscription_plans p
WHERE s.user_id = u.id
  AND upper(p.name) = 'BUSINESS'
  AND lower(u.email) IN ('vishalmishra66602@gmail.com', 'vishalmishra66602@gmail');
