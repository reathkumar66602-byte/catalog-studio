# CatalogStudio Microservices Architecture

## Overview

CatalogStudio uses a **strangler-fig** migration: domains are extracted from the modular Spring Boot platform into independently deployable services without breaking public API contracts (`/api/v1/**`, `{ success, message, data }`).

## Production runtime

| Component | Port | Status | Role |
| --- | --- | --- | --- |
| `api-gateway` | 8080 | **Live** | Public entry; routes wholesale vs platform |
| `supplier-service` | 8081 | **Live** | Wholesale / supplier management + goods receipts |
| `platform-api` (`catalog-studio-backend`) | 8082 | **Live** | Auth, products/AI, inventory, marketplace/OMS, billing, email, tips, admin |
| PostgreSQL | 5432 | **Live** | `catalog_studio` + `catalog_studio_supplier` (schema `public` each) |
| Redis | 6379 | **Live** | Cache / rate limits |
| RabbitMQ | 5672 | **Live (prod)** | Domain events (`catalogstudio.domain`) |

Only **supplier-service** has been extracted so far. Remaining domains stay in platform-api until a future extract wave.

## Gateway routing

- `/api/v1/wholesale/**` → `supplier-service`
- all other `/api/**`, swagger, actuator, `/internal/**` → `platform-api`

## Cross-service integration

- **JWT**: platform issues tokens (`userId`, `uid`, `role`). Supplier validates with shared secret.
- **Internal REST** (`X-Internal-Api-Key`):
  - `GET /internal/v1/users/{id}`
  - `GET /internal/v1/users?ids=`
  - `PUT /internal/v1/users/{id}/mobile`
  - `GET /internal/v1/billing/users/{id}/access`
  - `POST /internal/v1/ai/wholesale-autofill`
  - `POST /internal/v1/inventory/wholesale-receipts` (goods-receipt inventory credit)
- **Events** (RabbitMQ exchange `catalogstudio.domain`): order lifecycle / bill-ready → platform email listener.

## Feature flags

| Env | Meaning |
| --- | --- |
| `WHOLESALE_MODULE_ENABLED=false` | **Required.** Platform does not own wholesale APIs. |
| `MESSAGING_ENABLED=true` | Enables RabbitMQ publishers/listeners (prod default). |
| `INTERNAL_API_KEY` | Shared service-to-service key |

## Databases

| Database | Owner | Contents |
| --- | --- | --- |
| `catalog_studio` | platform-api | Users, products, inventory, billing, OMS, … |
| `catalog_studio_supplier` | supplier-service | `wholesale_*`, `goods_receipts`, `goods_receipt_items` |

Legacy monolith `wholesale_*` / goods-receipt tables on `catalog_studio` are dropped by Flyway **V51**.

## Local

```bash
docker compose up --build
```

- Gateway: http://localhost:8080
- Supplier: http://localhost:8081
- Platform: http://localhost:8082

## Module layout

```text
platform/platform-common|security|events
services/supplier-service
gateway/api-gateway
catalog-studio-backend             # platform-api
```

## Deploy (GCP)

```bash
scripts/gcp-deploy-microservices.sh
```

Compose overlay: `docker-compose.gcp.yml`.
