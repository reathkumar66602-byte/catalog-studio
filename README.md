# Catalog Studio

AI-powered product listing, image analysis, marketplace mapping, and Chrome autofill for e-commerce sellers.

Catalog Studio is an original SaaS workspace. The UI follows a typical seller registration → dashboard → AI listing → extension autofill workflow. It does not copy source code, logos, or branding from any marketplace or third-party product.

The application **never auto-submits** a marketplace listing. Sellers review AI suggestions, review filled fields, and submit the listing themselves.

## Repository layout

| Path | Description |
| --- | --- |
| `catalog-studio-backend/` | Spring Boot 3.4 / Java 21 API, PostgreSQL, Flyway, JWT, OpenAPI |
| `listingai-dashboard/` | React + Vite seller dashboard |
| `listingai-extension/` | Chrome Manifest V3 autofill extension |
| `docker-compose.yml` | PostgreSQL + API + dashboard |
| `.env.example` | Environment template |

## Prerequisites

- Java 21 (this project was compiled with `D:\jdk\jdk-21.0.10`)
- Maven 3.8+
- Node.js 20+
- Local PostgreSQL 16/17 (already running as `postgresql-x64-17` on this machine)

## Quick start (local)

Set JDK 21 for this session:

```powershell
$env:JAVA_HOME = "D:\jdk\jdk-21.0.10"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
```

### 1. Database

Create the app database in pgAdmin (or psql) using `scripts/create-db.sql`.

Default API connection in `application-dev.yml`:

- host: `localhost:5432`
- database: `catalog_studio`
- user/password: `catalog_studio` / `catalog_studio`

### 2. Backend

```powershell
cd catalog-studio-backend
mvn -DskipTests spring-boot:run
```

API: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui`

Seeded accounts (dev profile):

- Admin: `admin@catalogstudio.local` / `Admin@CatalogStudio1`
- Seller: `seller@catalogstudio.local` / `Seller@CatalogStudio1`
- Referral code: `ABC2026`

Default AI provider is `mock`, so analysis works without an API key.

To use OpenAI vision:

```
AI_PROVIDER=openai
AI_API_KEY=sk-...
AI_MODEL=gpt-4o-mini
```

### 3. Dashboard

```bash
cd listingai-dashboard
npm install
npm run dev
```

Open `http://localhost:5173`

Register at `/register?ref=ABC2026` or sign in with the seeded seller.

### 4. Chrome extension

```bash
cd listingai-extension
npm install
npm run build
```

In Chrome: `chrome://extensions` → Developer mode → Load unpacked → select `listingai-extension/dist`.

Then in the dashboard open **Chrome Extension**, generate a pairing key, and paste it into the extension popup.

On a supported supplier listing page, click the Catalog Studio button to open the sidebar. Choose a product and Autofill. Review fields, then submit the listing yourself.

## Core API

`POST /api/v1/product/analyze` (multipart)

- `images` — 1 to 5 JPG/PNG/WEBP files
- optional `marketplace`, `categoryHint`, `productTypeHint`, `primaryIndex`

Auth APIs live under `/api/v1/auth/*`. Extension APIs use header `X-Extension-Key`.

All responses use:

```json
{ "success": true, "message": "...", "data": {} }
```

## Product flow

Register → Login → Dashboard → Analyze images → Review/edit AI result → Save product → Map marketplace attributes → Chrome extension autofill → Seller submits listing.

AI values are stored with `source = AI`. Seller edits are stored with `source = USER`. Marketplace-mapped values use `source = MARKETPLACE`.

## Marketplace adapters

`MarketplaceAdapter` has Meesho, Amazon, and Flipkart implementations. Color/pattern/material mappings are stored in `marketplace_attribute_mappings` and can be changed without code deploys.

Meesho autofill uses label-based field matching first, then name, placeholder, accessibility label, data attributes, and CSS as a last resort.

## Docker

```bash
docker compose up --build
```

Dashboard: `http://localhost`  
API: `http://localhost:8080`

## Tests

```bash
cd catalog-studio-backend && mvn test
cd listingai-dashboard && npm test
cd listingai-extension && npm test
```

Covered scenarios include invalid image rejection, uncertain AI material, invalid AI JSON, revoked extension keys, and stop-autofill.

## Security notes

- Passwords hashed with BCrypt
- Refresh tokens and extension keys stored as SHA-256 hashes only
- JWT access tokens
- Rate limiting on API and auth routes
- File type and size validation
- Secrets via environment variables only
- No marketplace passwords stored

## Development phases in this repo

1. PostgreSQL schema + Spring entities  
2. Auth, referral codes, JWT  
3. React dashboard shell  
4–7. Image upload, `POST /api/v1/product/analyze`, mock/OpenAI providers, review UI  
8–9. Product CRUD, templates, profiles  
10–13. Chrome extension, Meesho autofill, Amazon/Flipkart adapters, pairing  
14. Subscriptions, admin, Docker, tests
