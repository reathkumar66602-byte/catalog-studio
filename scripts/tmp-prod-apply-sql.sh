#!/bin/bash
set -euo pipefail
echo "=== FLYWAY ==="
sudo docker exec catalog-studio-postgres-1 psql -U catalog_studio -d catalog_studio -c \
  "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"

echo "=== SUPERADMIN + PLAN ==="
sudo docker exec catalog-studio-postgres-1 psql -U catalog_studio -d catalog_studio -c \
  "SELECT u.email, u.role, u.status, p.name AS plan, s.status AS sub_status, s.end_date
   FROM users u
   LEFT JOIN LATERAL (
     SELECT * FROM subscriptions WHERE user_id = u.id ORDER BY created_at DESC LIMIT 1
   ) s ON true
   LEFT JOIN subscription_plans p ON p.id = s.plan_id
   WHERE lower(u.email) IN ('vishalmishra66602@gmail.com', 'vishalmishra66602@gmail')
      OR u.role IN ('SUPERADMIN', 'ADMIN');"
