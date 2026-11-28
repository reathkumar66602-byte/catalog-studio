# Catalog Studio — Manual Regression

Solo-friendly runner. After you deploy from `main`, run this yourself against **prod** or **staging**. Every run writes an HTML report you can open or download (ZIP).

## Quick start

```powershell
cd d:\catalog-studio\regression
copy .env.example .env
# edit .env → set PROD_TEST_EMAIL / PROD_TEST_PASSWORD (dedicated test seller)

# After deploying to production (read-only / safe):
.\run.ps1 -Env prod -OpenReport

# Local stack:
.\run.ps1 -Env local -OpenReport

# Staging with limited writes:
.\run.ps1 -Env staging -Mode full -OpenReport
```

`-OpenReport` opens the HTML in your browser and selects the ZIP in Explorer so you can download/share it.

## Environments

| `-Env` | Default API | Default mode |
| --- | --- | --- |
| `local` | `http://localhost:8080` | `safe` (use `-Mode full` for writes) |
| `staging` | `https://staging.catalogstudio.in` | `safe` / `full` allowed |
| `prod` | `https://www.catalogstudio.in` | **always `safe`** (writes refused) |

Prod **cannot** run `-Mode full`. That protects live customers.

## Suites covered

| Suite | What it checks |
| --- | --- |
| `smoke` | Dashboard up, public pages, gateway routes (platform / wholesale / supplier / support / extension / OMS / inventory) |
| `auth` | Login, `/me`, sessions, refresh (+ logout in full mode) |
| `catalog` | Products list, analysis history |
| `templates` | Listing templates + autofill profiles |
| `wholesale` | Buyer live-stock: categories, stores, products, orders |
| `supplier` | Supplier store, products, orders, coupons, bills |
| `inventory` | Inventory list + product variants |
| `oms` | Marketplaces, OMS orders/listings/couriers, Meesho/Flipkart/Amazon panel status |
| `extension` | Extension API (ping, devices, workspace, products, profiles) + Chrome autofill unit tests |
| `labels` | Public PDF crop/merge pages + Vitest crop/merge math |
| `calculator` | Public Meesho calculator page + profit math unit tests |
| `trending` | Trending categories + products (Meesho / Flipkart / Amazon) |
| `shoot` | Shoot list API + shoot contract unit tests |
| `tips` | App guide, tutorials, tips |
| `support` | Categories, tickets (+ create ticket in full mode) |
| `billing` | Plans, current subscription, entitlements |

Run a subset:

```powershell
.\run.ps1 -Env prod -Suite smoke,supplier,oms,extension,labels,calculator -OpenReport
```

## Reports (download)

Each run writes under `regression/reports/`:

| File | Purpose |
| --- | --- |
| `regression-<env>-<timestamp>.html` | Human report |
| `regression-<env>-<timestamp>.json` | Machine-readable |
| `regression-<env>-<timestamp>.zip` | **Download/share pack** (html+json) |
| `latest.html` / `latest.json` | Always overwritten to last run |

Exit code `0` = all non-skipped checks passed. `1` = failures.

## Prod safety rules

1. Use a **dedicated test seller**, not your personal admin account.
2. Prod is read-only: login + GET checks only (plus local Vitest for PDF/calculator/extension).
3. Do not commit `regression/.env`.

## Typical workflow

1. Code on `main`
2. Deploy
3. `.\run.ps1 -Env prod -OpenReport`
4. If FAIL → fix on main → redeploy → re-run
5. Keep/share the ZIP as a record of that deploy

**Note:** Labels, Meesho calculator, and extension autofill are mostly client-side — those suites run local Vitest against your repo code, and also hit the public pages on the chosen environment.
