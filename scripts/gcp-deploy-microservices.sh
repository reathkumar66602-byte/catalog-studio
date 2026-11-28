#!/usr/bin/env bash
# Run on the GCP VM after the source tarball is extracted into /opt/catalog-studio.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/catalog-studio}"
cd "$APP_DIR"

if [[ ! -f .env.gcp ]]; then
  echo "Missing $APP_DIR/.env.gcp"
  exit 1
fi

env_get() {
  local key="$1"
  local default="${2:-}"
  local value
  value="$(grep -E "^${key}=" .env.gcp | tail -n1 | cut -d= -f2- | tr -d '\r' || true)"
  if [[ -z "$value" ]]; then
    echo "$default"
  else
    echo "$value"
  fi
}

DB_PASS="$(env_get DATABASE_PASSWORD catalog_studio)"
PUBLIC_ORIGIN="$(env_get PUBLIC_ORIGIN https://www.catalogstudio.in)"
export DATABASE_PASSWORD="$DB_PASS"
export RABBITMQ_USER="$(env_get RABBITMQ_USER catalogstudio)"
export RABBITMQ_PASSWORD="$(env_get RABBITMQ_PASSWORD catalogstudio)"
export INTERNAL_API_KEY="$(env_get INTERNAL_API_KEY catalog-studio-internal)"
export MESSAGING_ENABLED=true
export WHOLESALE_MODULE_ENABLED=false
export SPRING_PROFILES_ACTIVE=prod
export DATABASE_URL="jdbc:postgresql://postgres:5432/catalog_studio"
export STORAGE_PUBLIC_BASE_URL="${PUBLIC_ORIGIN}/api/v1/files"
export SUPPLIER_STORAGE_PUBLIC_BASE_URL="${PUBLIC_ORIGIN}/api/v1/supplier-files"
export PLATFORM_BASE_URL="http://platform-api:8082"
export PUBLIC_ORIGIN

COMPOSE=(sudo docker compose --env-file .env.gcp -f docker-compose.yml -f docker-compose.gcp.yml)

echo "==> Removing stale paths left by tar extracts (deleted in git are not removed by tar)"
# Tar overlay does not delete removed files; force-clean known dead monolith/scaffold trees.
rm -rf \
  "${APP_DIR}/catalog-studio-backend/src/main/java/com/catalogstudio/wholesale" \
  "${APP_DIR}/catalog-studio-backend/src/test/java/com/catalogstudio/wholesale" \
  "${APP_DIR}/services/identity-service" \
  "${APP_DIR}/services/product-service" \
  "${APP_DIR}/services/marketplace-service" \
  "${APP_DIR}/services/billing-service" \
  "${APP_DIR}/services/notification-service" \
  "${APP_DIR}/services/media-service"

echo "==> Ensuring supplier and support databases exist"
"${COMPOSE[@]}" up -d postgres
sleep 5
if ! "${COMPOSE[@]}" exec -T postgres \
  psql -U catalog_studio -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='catalog_studio_supplier'" | grep -q 1; then
  "${COMPOSE[@]}" exec -T postgres \
    psql -U catalog_studio -d postgres -v ON_ERROR_STOP=1 -c \
    "CREATE DATABASE catalog_studio_supplier OWNER catalog_studio;"
fi
if ! "${COMPOSE[@]}" exec -T postgres \
  psql -U catalog_studio -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='catalog_studio_support'" | grep -q 1; then
  "${COMPOSE[@]}" exec -T postgres \
    psql -U catalog_studio -d postgres -v ON_ERROR_STOP=1 -c \
    "CREATE DATABASE catalog_studio_support OWNER catalog_studio;"
fi

echo "==> Building and starting microservices stack (this can take a while)"
"${COMPOSE[@]}" up -d --build --remove-orphans

echo "==> Waiting for supplier health"
for i in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8081/actuator/health >/dev/null 2>&1; then
    echo "supplier-service is up"
    break
  fi
  sleep 10
  if [[ "$i" -eq 90 ]]; then
    echo "supplier-service failed to become healthy"
    "${COMPOSE[@]}" logs --tail=120 supplier-service || true
    exit 1
  fi
done

echo "==> Waiting for support health"
for i in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8083/actuator/health >/dev/null 2>&1; then
    echo "support-service is up"
    break
  fi
  sleep 10
  if [[ "$i" -eq 90 ]]; then
    echo "support-service failed to become healthy"
    "${COMPOSE[@]}" logs --tail=120 support-service || true
    exit 1
  fi
done

echo "==> Waiting for platform health"
for i in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8082/actuator/health >/dev/null 2>&1; then
    echo "platform-api is up"
    break
  fi
  sleep 10
  if [[ "$i" -eq 90 ]]; then
    echo "platform-api failed to become healthy"
    "${COMPOSE[@]}" logs --tail=120 platform-api || true
    exit 1
  fi
done

echo "==> Waiting for gateway health"
for i in $(seq 1 30); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "api-gateway is up"
    break
  fi
  sleep 5
done

echo "==> Migrating wholesale data into supplier DB (if source tables exist)"
HAS_WHOLESALE=$("${COMPOSE[@]}" exec -T postgres \
  psql -U catalog_studio -d catalog_studio -tAc "SELECT to_regclass('public.wholesale_stores')" | tr -d '[:space:]')
if [[ "$HAS_WHOLESALE" == "wholesale_stores" ]]; then
  {
    echo "SELECT set_config('app.platform_password', '${DB_PASS}', false);"
    cat scripts/migrate-wholesale-to-supplier-db.sql
  } | "${COMPOSE[@]}" exec -T postgres \
      psql -U catalog_studio -d catalog_studio_supplier -v ON_ERROR_STOP=1
  echo "Wholesale migration completed"
else
  echo "No wholesale_stores in platform DB — skipping copy"
fi

echo "==> Smoke checks"
curl -fsS http://127.0.0.1:8080/actuator/health || true
echo
curl -fsS http://127.0.0.1:8080/api/v1/site | head -c 200; echo
curl -fsS http://127.0.0.1:8080/api/v1/wholesale/categories | head -c 200; echo
curl -fsS -o /dev/null -w "public_site=%{http_code}\n" https://www.catalogstudio.in/api/v1/site || true
curl -fsS -o /dev/null -w "public_wholesale=%{http_code}\n" https://www.catalogstudio.in/api/v1/wholesale/categories || true

"${COMPOSE[@]}" ps
echo "Deploy finished."
