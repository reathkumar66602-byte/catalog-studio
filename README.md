# Catalog Studio

Enterprise seller workspace for AI listings, marketplace tools, wholesale supplier management, and Chrome autofill.

Public API contract is stable: `/api/v1/**` with `{ success, message, data }`.

## Runtime architecture (what actually runs)

```text
Client (Dashboard / Extension)
        │
        ▼
  api-gateway :8080
        ├── /api/v1/wholesale/**  →  supplier-service :8081  →  DB catalog_studio_supplier
        └── /api/** (other)       →  platform-api :8082      →  DB catalog_studio
```

| Component | Port | Role | Database / schema |
| --- | --- | --- | --- |
| `gateway/api-gateway` | 8080 | Public entry | — |
| `services/supplier-service` | 8081 | Wholesale + goods receipt | `catalog_studio_supplier.public` |
| `catalog-studio-backend` (platform-api) | 8082 | Auth, products, inventory, billing, OMS, email | `catalog_studio.public` |
| Postgres | 5432 | Two databases, schema `public` each | — |
| Redis | 6379 | Cache / rate limits | — |
| RabbitMQ | 5672 | Domain events (prod; optional locally) | — |
| `catalog-studio-dashboard` | 5173 | React seller UI | — |

**Important:** Only **supplier-service** has been extracted from the monolith so far. Other domains remain in platform-api. See [`services/README.md`](services/README.md).

## Repository layout

| Path | Status | Description |
| --- | --- | --- |
| `gateway/api-gateway/` | **Live** | Spring Cloud Gateway |
| `services/supplier-service/` | **Live** | Wholesale / supplier microservice |
| `catalog-studio-backend/` | **Live** | Platform API (remaining domains) |
| `platform/` | **Live libs** | Shared `platform-common`, `platform-security`, `platform-events` |
| `catalog-studio-dashboard/` | **Live** | React + Vite dashboard |
| `catalog-studio-extension/` | **Live** | Chrome Manifest V3 extension |
| `docs/ARCHITECTURE_MICROSERVICES.md` | Docs | Boundaries and deploy notes |
| `docker-compose.yml` | Ops | Local / base stack |
| `docker-compose.gcp.yml` | Ops | Production GCP overlay |

## Data ownership

| Data | Database | Owner |
| --- | --- | --- |
| Users, sessions, products, inventory, billing, OMS | `catalog_studio` | platform-api |
| Wholesale stores, catalog, orders, coupons, goods receipts | `catalog_studio_supplier` | supplier-service |

Goods receipt flow: buyer confirms receipt on **supplier-service** → accepted qty credited on **platform-api** via internal API `POST /internal/v1/inventory/wholesale-receipts`.

Legacy monolith `wholesale_*` tables were removed from `catalog_studio` (Flyway V51). Do not reintroduce them on platform.

## Prerequisites

- Java 21
- Maven 3.8+
- Node.js 20+
- PostgreSQL 16/17
- Redis (recommended)
- RabbitMQ (required when `MESSAGING_ENABLED=true`)

## Quick start (microservices local)

```powershell
$env:JAVA_HOME = "D:\jdk\jdk-21.0.10"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# Preferred: full stack
docker compose up --build
```

Or run jars/processes individually (gateway 8080, platform 8082, supplier 8081, dashboard 5173).

Set on platform:

```text
WHOLESALE_MODULE_ENABLED=false
```

API entry: `http://localhost:8080`  
Dashboard: `http://localhost:5173`  
Swagger (via gateway/platform): `/swagger-ui`

### Dev accounts (when seeded)

- Admin: `admin@catalogstudio.local` / `Admin@CatalogStudio1`
- Seller: `seller@catalogstudio.local` / `Seller@CatalogStudio1`

### AI

Default provider is `mock`. For OpenAI:

```text
AI_PROVIDER=openai
AI_API_KEY=sk-...
AI_MODEL=gpt-4o-mini
```

## Dashboard & extension

```bash
cd catalog-studio-dashboard && npm install && npm run dev
cd catalog-studio-extension && npm install && npm run build
```

Load `catalog-studio-extension/dist` in Chrome (`chrome://extensions` → Developer mode).

## Production (GCP)

```bash
scripts/gcp-deploy-microservices.sh
```

Uses `docker-compose.yml` + `docker-compose.gcp.yml` with `.env.gcp`.  
Prod runs gateway + platform-api + supplier-service + Postgres + Redis + RabbitMQ (`MESSAGING_ENABLED=true`, `WHOLESALE_MODULE_ENABLED=false`).

## Documentation

- [`docs/ARCHITECTURE_MICROSERVICES.md`](docs/ARCHITECTURE_MICROSERVICES.md) — topology and integration
- [`services/README.md`](services/README.md) — live vs planned services
