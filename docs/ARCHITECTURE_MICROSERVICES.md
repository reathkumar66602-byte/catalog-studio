# CatalogStudio Microservices Architecture

## Overview

CatalogStudio is migrating from a modular Spring Boot monolith to independently deployable services using a **strangler-fig** approach. Public API contracts (`/api/v1/**`, `{ success, message, data }`) are preserved so the React dashboard and Chrome extension need no URL changes.

## Runtime topology

| Component | Port | Role |
| --- | --- | --- |
| `api-gateway` | 8080 | Public entry; routes wholesale vs platform |
| `supplier-service` | 8081 | Wholesale / Supplier Management (extracted) |
| `platform-api` (`catalog-studio-backend`) | 8082 (compose) / 8080 (legacy local) | Remaining domains |
| PostgreSQL | 5432 | `catalog_studio` + `catalog_studio_supplier` |
| Redis | 6379 | Shared cache / rate limits |
| RabbitMQ | 5672 | Domain events |

## Service boundaries

| Service | Status | Owns |
| --- | --- | --- |
| **supplier-service** | Extracted | Stores, GST, catalog, stock, coupons, orders, bills (`wholesale_*`) |
| **platform-api** | Live | Auth, products/AI, marketplace/OMS, billing, email, tips, admin, … |
| identity-service | Scaffold | Future auth/users extract |
| product-service | Scaffold | Future product/AI/shoot extract |
| marketplace-service | Scaffold | Future OMS/extension extract |
| billing-service | Scaffold | Future subscriptions/payments extract |
| notification-service | Scaffold | Future email/campaigns extract |
| media-service | Scaffold | Future storage extract |

## Gateway routing

- `/api/v1/wholesale/**` → `supplier-service`
- all other `/api/**`, swagger, actuator → `platform-api`

## Cross-service integration

- **JWT**: platform issues tokens with `userId`, `uid`, `role`. Supplier validates locally (shared secret).
- **Internal REST** (header `X-Internal-Api-Key`):
  - `GET /internal/v1/users/{id}`
  - `GET /internal/v1/users?ids=`
  - `PUT /internal/v1/users/{id}/mobile`
  - `GET /internal/v1/billing/users/{id}/access`
  - `POST /internal/v1/ai/wholesale-autofill`
- **Events** (RabbitMQ exchange `catalogstudio.domain`): order lifecycle / bill-ready → platform email listener.

## Feature flags

| Env | Meaning |
| --- | --- |
| `WHOLESALE_MODULE_ENABLED=false` | Platform stops exposing wholesale APIs (supplier owns them) |
| `MESSAGING_ENABLED=true` | Enables RabbitMQ publishers/listeners |
| `INTERNAL_API_KEY` | Shared service-to-service key |

## Local / compose

```bash
docker compose up --build
```

- API (gateway): http://localhost:8080  
- Supplier direct: http://localhost:8081  
- Platform direct: http://localhost:8082  

### Legacy monolith-only (no extract)

```bash
cd catalog-studio-backend
# WHOLESALE_MODULE_ENABLED defaults true
mvn spring-boot:run
```

## Data cutover

1. Ensure `catalog_studio_supplier` exists (compose init script or `scripts/init-supplier-db.sql`).
2. Start supplier-service once so Flyway creates schema.
3. Run `scripts/migrate-wholesale-to-supplier-db.sql` (extend as needed for full table set).
4. Set `WHOLESALE_MODULE_ENABLED=false` on platform and route via gateway.
5. Rollback: re-enable wholesale on platform and point gateway wholesale route back to platform.

## Module layout

```text
platform/platform-common|security|events
services/supplier-service (+ scaffolds)
gateway/api-gateway
catalog-studio-backend   # platform-api
```
