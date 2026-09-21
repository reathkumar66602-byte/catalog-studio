# Publish Catalog Studio Autofill (Chrome Web Store)

Chrome does **not** load a Java backend JAR. The live API already runs on the GCP VM. The store package is a **ZIP** of the extension `dist` folder. This ZIP is what you upload to Google.

Live URLs baked into the production build:

- Dashboard: `https://www.catalogstudio.in`
- API: `https://www.catalogstudio.in/api/v1`
- Privacy policy (required by the store): `https://www.catalogstudio.in/privacy`

## 1. Confirm the live API is up

On the GCP VM the Docker stack must be running (`docker-compose.yml` + `docker-compose.gcp.yml`). From your PC:

```powershell
curl https://www.catalogstudio.in/api/v1/site
```

You should get JSON. If this fails, fix DNS/SSL/VM first. The extension will not pair until the site is reachable.

Deploy the dashboard build that includes `/privacy` before you submit the store listing.

## 2. Build the production extension ZIP

From the repo root on Windows:

```powershell
powershell -File scripts/build-extension-prod.ps1
```

Or from `catalog-studio-extension`:

```powershell
npm install
npm run build:prod
```

Output:

| Path | Use |
| --- | --- |
| `catalog-studio-extension/dist/` or `dist-local/` | Load unpacked against local API |
| `catalog-studio-extension/dist-prod/` | Load unpacked against the live API |
| `catalog-studio-extension/release/catalog-studio-extension-1.4.0.zip` | Upload this to the Chrome Web Store |

The prod zip **does not** include localhost permissions. `npm run build` emits both a localhost unpacked folder (`dist` / `dist-local`) and a production unpacked folder plus zip (`dist-prod` / `release`).

## 3. Test the ZIP against the live site (before Google review)

1. Open Chrome and go to `chrome://extensions`.
2. Turn on **Developer mode**.
3. Click **Load unpacked**.
4. Select `d:\catalog-studio\catalog-studio-extension\dist-prod` (the folder that contains `manifest.json`, not the zip, not `src`).
5. Open `https://www.catalogstudio.in/login`, sign in, and open `/extension`. Pairing should happen automatically.
6. Open Meesho **Add Single Catalog**, click the Catalog Studio button, Generate, then Fill Values for Form. Confirm it does **not** submit the listing for you.

If an older unpacked build was pointing at localhost, click **Remove** first, then load this `dist-prod` again.

To install from the zip itself: unzip it to a folder, then Load unpacked on that folder. Chrome cannot load a `.zip` or `.jar` directly.

## 4. Chrome Web Store developer account (one-time)

1. Open [Chrome Web Store Developer Dashboard](https://chrome.google.com/webstore/devconsole).
2. Sign in with the Google account that should own Catalog Studio.
3. Accept the [developer agreement](https://developer.chrome.com/docs/webstore/program-policies).
4. Pay the one-time registration fee (currently USD $5).
5. Complete account identity / trader verification if Google asks (required for public listings in many regions).

## 5. Upload the ZIP and fill the listing

1. In the dashboard click **New item**.
2. Upload `catalog-studio-extension/release/catalog-studio-extension-1.4.0.zip`.
   `manifest.json` must be at the **root** of the zip (the build script already does this).
3. **Store listing**
   - Name: Catalog Studio Autofill
   - Summary: Detect the Meesho store, generate listing details from the product photo, and fill the catalog form. Never auto-submits.
   - Category: Productivity (or Shopping)
   - Language: English
   - Official URL: `https://www.catalogstudio.in`
   - Support URL: `https://www.catalogstudio.in/contact`
4. **Graphics** (required)
   - Store icon: 128×128 — use `catalog-studio-extension/public/icons/icon128.png`
   - At least one screenshot 1280×800 or 640×400 of the Meesho fill flow and the paired popup
   - Optional small/large promo tiles
5. **Privacy**
   - Privacy policy URL: `https://www.catalogstudio.in/privacy`
   - Single purpose: help Catalog Studio sellers generate and fill Meesho listing fields
   - Justify `storage` (pairing key, API URL, seller profile) and `scripting` (inject sidebar on Meesho)
   - Justify host access: `catalogstudio.in` (pair + API) and `meesho.com` / `supplier.meesho.com` / `images.meesho.com` (read page + fill form + product photos)
   - Declare that you collect account/business data used to fill listings, and that you do not sell data
6. **Distribution**
   - Visibility: **Unlisted** first (recommended), then Public after a successful test install from the store URL
   - Regions: start with India

## 6. Submit and go live

1. Click **Submit for review**.
2. Review is often 1–3 days; it can take longer the first time.
3. When approved, the item is live at a `chromewebstore.google.com/detail/...` URL.
4. Put that URL on `https://www.catalogstudio.in/chrome-extension` and tell sellers to install from the store (unpacked + Developer mode is only for you).

Later updates: bump `version` in `catalog-studio-extension/package.json` **and** `public/manifest.json` (must be higher than the last uploaded version), run `npm run build:prod`, upload the new zip on the same item.

## Local vs production builds

| Command | Output | API | Use |
| --- | --- | --- | --- |
| `npm run build` | `dist` + `dist-local` (local) and `dist-prod` + `release/*.zip` (production) | localhost **and** catalogstudio.in | Daily build: both unpacked folders |
| `npm run build:local` | `dist` + `dist-local` | `http://localhost:8080/api/v1` | Local unpacked only |
| `npm run build:prod` | `dist-prod` + `release/*.zip` | `https://www.catalogstudio.in/api/v1` | Live test + Chrome Web Store |

Do not upload the localhost zip to the store. Google will reject localhost host permissions for a public product.
