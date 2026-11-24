# Catalog Studio — Marketplace OMS registration & configuration

**Company:** Catalog Studio  
**Website:** https://www.catalogstudio.in  
**Primary contact email:** support@catalogstudio.in  
**Primary mobile:** +91 9560111849  
**Product purpose:** Multi-channel e-commerce management SaaS (catalog, inventory, orders, labels) for independent retailers on Amazon, Flipkart, and Meesho.

This document is the runbook for registering Catalog Studio as a **marketplace API partner / public developer**, then wiring credentials into the Catalog Studio application (`.env` / deployment secrets).

---

## What this AI session did vs what only you can do

### Done in the repo (this session)

1. Mapped how Catalog Studio already expects OMS credentials (`FLIPKART_*`, `AMAZON_*`, `MEESHO_*` in `.env` / `application.yml` / `docker-compose.gcp.yml`).
2. Confirmed Marketplaces UI path: Dashboard → **Marketplaces** (super-admin) → Platform OMS sync panel.
3. Prepared ready-to-paste company / app descriptions for Amazon & Flipkart forms.
4. Documented official portals, roles, redirect URLs, security answers outline, and post-approval env mapping.
5. Corrected `.env.example` comments so Flipkart is treated as **third-party / API Partner**, not seller self-access.

### Cannot be completed by an agent (must be done by you)

Amazon and Flipkart require **human identity verification**, **OTP to your phone/email**, **legal acceptance of terms**, and often a **video ID check**. No one else can finish those steps for Catalog Studio.

| Step | Why you must do it |
|------|--------------------|
| OTP on `9560111849` / `support@catalogstudio.in` | Codes arrive only on your devices |
| Government photo ID + address proof upload | Legal identity of the business admin |
| Amazon video identity call | Live verification with Amazon |
| Accept SPP / Partner terms | Binding company agreement |
| Paste issued App ID / Client Secret into production secrets | Secrets must stay in your vault / hosting env |

**After you receive credentials**, paste them into production `.env` (or GCP secret manager) and set the matching `*_OMS_ENABLED=true` flags. Do **not** commit secrets to git.

---

## Shared business details (use on every form)

| Field | Value |
|-------|--------|
| Legal / trade name | Catalog Studio |
| Website | https://www.catalogstudio.in |
| Support email | support@catalogstudio.in |
| Phone | +91 9560111849 |
| Product type | Multi-channel e-commerce OMS + catalog SaaS |
| Customers | Multiple independent Indian retailers (not only Catalog Studio’s own seller account) |
| Privacy page | https://www.catalogstudio.in/privacy |
| Contact page | https://www.catalogstudio.in/contact |

### Ready-to-paste application description (Amazon + Flipkart)

> Catalog Studio (https://www.catalogstudio.in) is a multi-channel e-commerce management SaaS for independent retailers in India. Sellers connect their Amazon, Flipkart, and Meesho accounts to manage product catalogs, inventory/stock sync, order processing, and shipping labels from one dashboard. Catalog Studio never sells on behalf of Amazon/Flipkart without seller authorization. Each retailer explicitly authorizes our app; we store tokens encrypted, sync only the data needed for OMS (orders, listings, inventory, shipment labels), and do not resell marketplace data. Contact: support@catalogstudio.in · +91 9560111849.

### Use cases checklist (for forms)

- Central catalog / listing management across marketplaces  
- Inventory and price sync to reduce oversell  
- Order pull and status sync for fulfillment  
- Shipping label / invoice retrieval where APIs allow  
- Seller-authorized multi-tenant access (OAuth / partner flows)

---

## A) Amazon — Register Catalog Studio as a Public SP-API developer

### Official entry points

1. **Solution Provider Portal (SPP):** https://solutionproviderportal.amazon.com/  
2. **Public developer guide:** https://developer-docs.amazon.com/sp-api/docs/register-as-a-public-developer  
3. **Identity verification:** https://developer-docs.amazon.com/sp-api/docs/solution-provider-identity-verification  
4. **Create SPP account steps:** https://developer-docs.amazon.com/sp-api/docs/onboarding-step-2-create-an-spp-account  

### Why Public Developer

Catalog Studio serves **multiple independent retailers**. That requires:

- **Public Developer** (not Private / own-seller-only)  
- Seller authorization via **OAuth**  
- Listing in the **Amazon Selling Partner Appstore** under public developer rules  

You do **not** need a Professional Selling account only to develop SP-API apps when registering via SPP.

### Step-by-step

1. **Create / sign in to an Amazon account** dedicated to Catalog Studio ops (prefer `support@catalogstudio.in`).
2. Open **https://solutionproviderportal.amazon.com/** → register for Solution Provider Portal.
3. Enroll and select **Build applications that use SP APIs**.
4. **Complete identity verification**
   - Business details for Catalog Studio  
   - Phone OTP to **9560111849**  
   - Primary contact: support email + mobile above  
   - Upload: government photo ID + proof of address (bank/credit statement ≤ 180 days)  
   - Complete Amazon’s video identity check when prompted  
5. Open **Settings → Developer Profile** (or Develop Apps → Proceed to Developer Profile).
6. Fill **Contact Information**
   - Organization: Catalog Studio  
   - Website: https://www.catalogstudio.in (must be public HTTPS, not login-only)  
   - Ensure privacy / contact pages are reachable  
7. **Data Access:** choose  
   **Public Developer: I build and offer publicly available applications that are used by other sellers.**
8. **Request API roles** needed for Catalog Studio OMS (select only what you will use):

   | Role area | Why Catalog Studio needs it |
   |-----------|-----------------------------|
   | Orders / Order-related | Pull and manage seller orders |
   | Listings / Product Listing | Catalog sync |
   | Inventory / Fulfillment Inventory | Stock push |
   | Shipping / Easy Ship / Merchant Fulfillment (as applicable) | Labels & shipment |

   If you request **restricted roles** (PII such as buyer address), Amazon may require a deeper architecture / security review. Prefer starting with non-restricted roles if you can ship without PII first; add restricted roles later with a clear PII data-flow story.

9. **Use Cases:** paste the application description above (keep free-text answers under Amazon’s character limits, typically &lt; 500 chars per field where stated).
10. **Security & data protection questions** — answer truthfully based on Catalog Studio’s backend:
    - Credentials / tokens encrypted at rest (app uses credential crypto for marketplace secrets)  
    - Access limited to authenticated tenants / super-admin ops  
    - HTTPS only for APIs and callbacks  
    - No selling or sharing of Amazon seller/buyer data with unrelated third parties  
    - Incident response / access revocation when a seller disconnects  
11. Acknowledge AUP, DPP, and Solution Provider Portal Agreement → **Register / Submit**.
12. Watch **support@catalogstudio.in** for case questions. **Respond within 5 days** or the case can close.
13. After the developer profile is approved / eligible:
    - Register your **production SP-API application** in SPP / Develop Apps  
    - Configure **OAuth** (Login with Amazon) redirect URIs for seller authorization  
    - Note **LWA Client ID** and **Client Secret**  
    - Complete Appstore listing requirements for public apps  

### Suggested OAuth redirect (align with backend when implemented)

```text
https://www.catalogstudio.in/api/v1/marketplaces/amazon/callback
```

Use the exact HTTPS route your backend exposes. Until OAuth callback is implemented, you can still create the SPP app and keep sandbox testing with authorized test sellers.

### Map Amazon credentials into Catalog Studio

After approval, set production secrets (never commit):

```env
AMAZON_OMS_ENABLED=true
AMAZON_LWA_CLIENT_ID=<from SP-API / LWA app>
AMAZON_LWA_CLIENT_SECRET=<from SP-API / LWA app>
AMAZON_LWA_REFRESH_TOKEN=<seller-authorized refresh token; per seller in multi-tenant OAuth>
AMAZON_MARKETPLACE_ID=A21TJRUUN4KGV
AMAZON_SP_API_SANDBOX=false
```

Notes:

- `A21TJRUUN4KGV` = Amazon.in marketplace id (already default in Catalog Studio).  
- Public multi-seller SaaS should store **per-seller refresh tokens** after OAuth, not one global refresh token forever. The platform OMS env vars are the bootstrap / ops path already wired in `application.yml`; per-tenant OAuth is the long-term public-app model.  
- Keep `AMAZON_SP_API_SANDBOX=true` until production approval and first live seller authorization.

### Catalog Studio code touchpoints

- Env / config: `catalog-studio-backend/src/main/resources/application.yml` → `oms.amazon`  
- Admin OMS: Marketplaces page → Platform OMS  
- SP-API controller: `/api/v1/marketplaces/amazon/sp-api`

---

## B) Flipkart — Register Catalog Studio as an API Partner (third-party)

### Official entry points

1. **Partner registration:** https://www.partner.flipkart.com/register  
2. **Seller API docs (FMSAPI):** https://seller.flipkart.com/api-docs/FMSAPI.html  
3. **Auth tutorial (third-party authorization code):** https://www.seller.flipkart.com/api-docs/listing-api-docs/authTut.html  

### Critical rule

| Application type | Allowed for Catalog Studio SaaS? |
|------------------|----------------------------------|
| **Third-party / API Partner** | **Yes** — required for aggregators serving many sellers |
| **Self-access** (Seller Dashboard → Developer Access) | **No** — Flipkart forbids sharing self-access credentials with third-party aggregators |

Do **not** create a self-access application for Catalog Studio’s multi-retailer product.

### Step-by-step

1. Open **https://www.partner.flipkart.com/register**.
2. Register Catalog Studio as a partner using:
   - Email: `support@catalogstudio.in`  
   - Mobile: `9560111849` (OTP)  
   - Website: https://www.catalogstudio.in  
3. When asked **Are you an API Partner?** → select **Yes**.
4. Submit company + application information (use the shared description above).
5. Wait for Flipkart verification (docs indicate contact / response within ~**72 hours**).
6. After verification, open **Profile → Manage API Access**.
7. **Create Application** (third-party):
   - **Application name:** `Catalog Studio OMS`  
   - **Description:** multi-channel e-commerce management SaaS for Flipkart sellers (catalog, inventory, orders, labels)  
   - **Redirect URL (HTTPS required):**

```text
https://www.catalogstudio.in/api/v1/marketplaces/flipkart/callback
```

8. Create the application and **securely store**:
   - Application ID → `FLIPKART_APP_ID`  
   - Application Secret → `FLIPKART_APP_SECRET`  
9. Keep secrets in password manager / GCP Secret Manager — never in git, screenshots, or shared chat.

### Map Flipkart credentials into Catalog Studio

```env
FLIPKART_OMS_ENABLED=true
FLIPKART_APP_ID=<Application ID from Partner Dashboard>
FLIPKART_APP_SECRET=<Application Secret>
FLIPKART_LOCATION_ID=<seller location id when required for inventory>
```

### Important engineering note (Catalog Studio today)

- Partner apps use **Authorization Code** OAuth (seller Allow → redirect with `code` → exchange for tokens).  
- The current Flipkart HTTP client in Catalog Studio still uses **`grant_type=client_credentials`** (self-access style token call) with `FLIPKART_APP_ID` / `FLIPKART_APP_SECRET`.  
- **Action after partner app is approved:** implement `/api/v1/marketplaces/flipkart/callback` and switch token exchange to the **third-party authorization code flow** so each retailer can connect their Flipkart seller account from Catalog Studio. Until then, partner App ID/Secret are still the correct credentials to obtain and store.

### Catalog Studio code touchpoints

- Env / config: `oms.flipkart` in `application.yml`  
- Controller: `/api/v1/marketplaces/flipkart/seller-api`  
- Client: `FlipkartSellerApiClient`  
- Planned callback (register this URL in Flipkart now; implement if missing):  
  `/api/v1/marketplaces/flipkart/callback`

---

## C) Meesho — Supplier panel + partner API path

Meesho does **not** use the same public “create app in a portal” flow as Amazon SPP / Flipkart Partner Dashboard.

### What Catalog Studio supports today

Meesho OMS is wired for **Supplier Panel** credentials:

```env
MEESHO_OMS_ENABLED=true
MEESHO_PANEL_EMAIL=support@catalogstudio.in
MEESHO_PANEL_PASSWORD=<Meesho supplier panel password — set only in secrets>
MEESHO_PANEL_BASE_URL=https://supplier.meesho.com
MEESHO_PANEL_LIVE_AUTH_REQUIRED=false
```

1. Create / use a Meesho **Supplier** account at https://supplier.meesho.com with `support@catalogstudio.in` and mobile `9560111849`.  
2. Put panel email/password only in deployment secrets.  
3. Enable `MEESHO_OMS_ENABLED=true` when ready to sync.

### Official partner / OMS API (for multi-seller SaaS scale)

Established OMS partners (e.g. Uniware, EasyEcom, Fynd Konnect) onboard via Meesho integration email, typically:

- **meesholink-integration@meesho.com**  
- Sometimes also **integration@meesho.com** for cross-integration issues  

#### Suggested partner onboarding email (copy/paste)

**To:** meesholink-integration@meesho.com  
**Subject:** Request for Catalog Studio OMS API credentials / partner integration  

```text
Hello Meesho Integrations Team,

We operate Catalog Studio (https://www.catalogstudio.in), a multi-channel e-commerce
management SaaS for Indian retailers (catalog, inventory, orders, shipping labels).

We request partner/OMS API onboarding for Catalog Studio so independent Meesho
suppliers can authorize our platform.

Company: Catalog Studio
Website: https://www.catalogstudio.in
Contact email: support@catalogstudio.in
Contact phone: +91 9560111849
Meesho Supplier Panel email (ops): support@catalogstudio.in

Please share the partner integration process, required identifiers
(e.g. supplier identifier / external_identifier per location), and refresh tokens
or API credentials for Catalog Studio.

Thank you,
Catalog Studio
support@catalogstudio.in
```

Until Meesho issues partner API credentials, keep using the panel-based OMS path already in the product.

---

## Post-registration checklist (enable OMS in Catalog Studio)

1. Put secrets in production env / Secret Manager (not git).  
2. Set:

```env
FLIPKART_OMS_ENABLED=true
AMAZON_OMS_ENABLED=true
MEESHO_OMS_ENABLED=true
AMAZON_SP_API_SANDBOX=false   # only after Amazon production app is live
```

3. Redeploy backend (`docker-compose.gcp.yml` already passes these vars).  
4. Sign in as **super-admin** → **Marketplaces**.  
5. Confirm Platform OMS channel chips show **Connected** (enabled + configured).  
6. Run **Sync orders** / **Update stock** / **Get labels** per channel.  
7. Store App IDs / secrets / LWA values in your password manager with owners: Catalog Studio ops.

### Credential inventory (fill after approval)

| Platform | Credential | Env var | Stored? |
|----------|------------|---------|---------|
| Amazon | LWA Client ID | `AMAZON_LWA_CLIENT_ID` | ☐ |
| Amazon | LWA Client Secret | `AMAZON_LWA_CLIENT_SECRET` | ☐ |
| Amazon | Refresh token(s) | `AMAZON_LWA_REFRESH_TOKEN` / per-seller DB | ☐ |
| Flipkart | Application ID | `FLIPKART_APP_ID` | ☐ |
| Flipkart | Application Secret | `FLIPKART_APP_SECRET` | ☐ |
| Flipkart | Location ID | `FLIPKART_LOCATION_ID` | ☐ |
| Meesho | Panel email | `MEESHO_PANEL_EMAIL` | ☐ |
| Meesho | Panel password | `MEESHO_PANEL_PASSWORD` | ☐ |
| Meesho | Partner API tokens (future) | TBD after Meesho reply | ☐ |

---

## Website readiness for Amazon public review

Before Amazon reviews your public developer profile, confirm:

- [ ] https://www.catalogstudio.in loads over HTTPS  
- [ ] Product / OMS value is understandable without logging in  
- [ ] https://www.catalogstudio.in/privacy is public  
- [ ] https://www.catalogstudio.in/contact shows `support@catalogstudio.in`  
- [ ] No “under construction” or security interstitial  

---

## Recommended order of work

1. **Flipkart Partner register** (faster partner verification ~72h) — create third-party app + save App ID/Secret.  
2. **Amazon SPP** — identity verification + Public Developer profile (longest review).  
3. **Meesho** — supplier panel for current OMS; send partner API email in parallel.  
4. Paste credentials into production secrets and enable OMS flags.  
5. Implement Flipkart (and Amazon) **OAuth callbacks** for true multi-seller connect if not already live.

---

## References

- Amazon public developer registration: https://developer-docs.amazon.com/sp-api/docs/register-as-a-public-developer  
- Amazon SPP: https://solutionproviderportal.amazon.com/  
- Flipkart FMSAPI / application types: https://seller.flipkart.com/api-docs/FMSAPI.html  
- Flipkart Partner register: https://www.partner.flipkart.com/register  
- Catalog Studio env template: `/.env.example` (Platform OMS section)
