import { DEFAULT_API_BASE } from "../config";
import { fillByHints, resetAutofill, stopAutofill } from "./shared/autofillEngine";
import { syncGenerateEnabled } from "./shared/generateButton";
import { detectMarketplace, type MappedListing } from "./shared/marketplaces";
import { isMeeshoAddCatalogFlow, isMeeshoBulkCatalogPage, isMeeshoBulkTemplateStep, isMeeshoCatalogListPage, isMeeshoCatalogPage, isMeeshoCategoryPickerVisible, isMeeshoProductDetailsPage, readMeeshoCategoryFromPage, readMeeshoCategoryPath, shouldScanMeeshoForm, suggestedCategoryLabel } from "./shared/meeshoCatalog";
import { captureBestPageImage, watchMeeshoPageImages, type PageImageSource } from "./shared/meeshoPageImage";
import { detectMeeshoStore, isMeeshoPageChrome, sanitizeStoreName, type MeeshoStore } from "./shared/meeshoStore";
import { buildStyleCode, defaultsForCategory, deriveBrand, deriveFabric, deriveGenericName, deriveMainCategory, deriveOccasion, detectOrnamentation, extractPincode, mapGarmentLength, mapNeck, mapSleeveLength, mapSleeveStyling } from "./shared/meeshoDefaults";
import { fillSizeChart, fillSizeChoices, detectPageSizes, detectSelectedPageSizes, fallbackSizesForListing, planMeeshoFill } from "./shared/meeshoFormFill";
import { isInvalidatedContext, sendRuntimeMessage, storageGet, storageSet, watchStorageChanges } from "../services/chromeAccess";
import { getSession } from "../services/storage";
import { startFillSession, stopFillSession } from "./shared/fillSession";
import { meeshoUid } from "./shared/meeshoStore";
import { applyStaticI18n, currentLocale, loadLocale, localeOptionsHtml, setLocale, t } from "../i18n/runtime";

type ProductRecord = Record<string, unknown> & {
  id?: string;
  name?: string;
  productType?: string;
  category?: string;
  subcategory?: string;
  gender?: string;
  primaryColor?: string;
  pattern?: string;
  material?: string;
  sleeveType?: string;
  neckType?: string;
  fit?: string;
  occasion?: string;
  description?: string;
  images?: { url: string; primary?: boolean }[];
  titles?: { title: string; selected?: boolean }[];
};

type GeneratedState = {
  productId?: string;
  productType?: string;
  category?: string;
  subCategory?: string;
  gender?: string;
  primaryColor?: string;
  pattern?: string;
  printType?: string;
  material?: string;
  sleeveType?: string;
  neckType?: string;
  fit?: string;
  occasion?: string;
  style?: string;
  comboOf?: string;
  netQuantity?: string;
  ornamentation?: string;
  genericName?: string;
  titles: string[];
  descriptions: string[];
  selectedTitle: number;
  selectedDesc: number;
};

let pickedFile: File | null = null;
let pickedSource: "page" | "user-drop" | "" = "";
let selected: ProductRecord | null = null;
let generated: GeneratedState | null = null;
let watchersStarted = false;
let detectedStore: MeeshoStore | null = null;
let businessProfile: { name?: string; address?: string; gstNumber?: string } | null = null;
let selectedSizes = new Set<string>();
let availableSizes: string[] = [];
let lastSizeKey = "";
let pageSyncTimer = 0;
let shopLock: "ok" | "wait" | "bad" = "wait";
let shopLockMessage = "";
let sellerSettings: Record<string, any> = {};
let quota: { used?: number; limit?: number; remaining?: number; plan?: string } = {};
let categoryOptions: Array<{ id: string; name: string; path: string }> = [];
const SIDEBAR_DISMISS_KEY = "csSidebarDismissedPath";
let sidebarDismissedPath = "";

function whenReady(fn: () => void) {
  if (document.body) {
    fn();
    return;
  }
  document.addEventListener("DOMContentLoaded", fn, { once: true });
}

function start() {
  try {
    injectUi();
    watchSpaNavigation();
    startPageWatchers();
    listenRuntime();
  } catch (error) {
    console.warn("Catalog Studio could not start on this page", error);
  }
}

function listenRuntime() {
  try {
    chrome.runtime.onMessage.addListener((msg) => {
      if (msg?.type === "CS_TOGGLE") {
        const sidebar = document.getElementById("cs-sidebar");
        if (sidebar?.classList.contains("open")) {
          closeSidebarByUser();
        } else {
          openSidebarByUser();
        }
      }
      if (msg?.type === "CS_TICK") {
        window.postMessage({ type: "CS_TICK" }, location.origin);
      }
    });
  } catch {
    // ignore
  }
}

function pageKey() {
  return location.pathname;
}

function rememberedDismissedPath() {
  try {
    sidebarDismissedPath = sessionStorage.getItem(SIDEBAR_DISMISS_KEY) || sidebarDismissedPath;
  } catch {
    // private mode
  }
  return sidebarDismissedPath;
}

function isSidebarDismissed() {
  return rememberedDismissedPath() === pageKey();
}

function setSidebarDismissed(on: boolean) {
  sidebarDismissedPath = on ? pageKey() : "";
  try {
    if (on) sessionStorage.setItem(SIDEBAR_DISMISS_KEY, sidebarDismissedPath);
    else sessionStorage.removeItem(SIDEBAR_DISMISS_KEY);
  } catch {
    // private mode
  }
}

function openSidebarByUser() {
  setSidebarDismissed(false);
  document.getElementById("cs-sidebar")?.classList.add("open");
}

function closeSidebarByUser() {
  setSidebarDismissed(true);
  document.getElementById("cs-sidebar")?.classList.remove("open");
}

function maybeAutoOpenSidebar() {
  if (isSidebarDismissed()) return;
  if (isMeeshoAddCatalogFlow()) {
    document.getElementById("cs-sidebar")?.classList.add("open");
  }
}

function isCatalogStudioNode(node: Node | null) {
  const el = node && node.nodeType === Node.ELEMENT_NODE ? (node as Element) : node?.parentElement;
  if (!el) return false;
  return Boolean(el.closest("#cs-sidebar, #cs-fab") || el.id === "cs-sidebar" || el.id === "cs-fab" || el.id === "cs-style");
}

function schedulePageSync(forceFormScan = false) {
  if (pageSyncTimer) return;
  pageSyncTimer = window.setTimeout(() => {
    pageSyncTimer = 0;
    syncPageHint();
    syncStoreFromPage();
    if (forceFormScan || shouldScanMeeshoForm()) {
      syncCategoryFromPage();
      syncSizesFromPage();
    }
    maybeAutoOpenSidebar();
  }, 400);
}

function startPageWatchers() {
  if (watchersStarted) return;
  watchersStarted = true;
  watchMeeshoPageImages((file, source) => {
    if (!shouldScanMeeshoForm() && source === "page-scan") return;
    if (source === "page-scan" && pickedSource === "user-drop") return;
    previewLocalImage(file, "page", source);
    maybeAutoOpenSidebar();
    if (shouldScanMeeshoForm()) syncSizesFromPage();
  });
  document.addEventListener("click", (event) => {
    const target = event.target as HTMLElement | null;
    if (target?.closest?.("#cs-sidebar, #cs-fab")) return;
    schedulePageSync(!isMeeshoCatalogListPage() || isMeeshoAddCatalogFlow());
  }, true);
  const observer = new MutationObserver((mutations) => {
    if (mutations.every((mutation) => isCatalogStudioNode(mutation.target))) return;
    schedulePageSync();
  });
  observer.observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ["class", "aria-pressed", "aria-selected", "aria-checked"] });
  window.setInterval(() => {
    if (shouldScanMeeshoForm()) syncSizesFromPage();
  }, 2500);
}

function watchSpaNavigation() {
  const notify = () => {
    injectUi();
    schedulePageSync();
  };
  const wrap = (method: "pushState" | "replaceState") => {
    const original = history[method].bind(history);
    history[method] = (...args: Parameters<History["pushState"]>) => {
      const result = original(...args);
      schedulePageSync();
      return result;
    };
  };
  wrap("pushState");
  wrap("replaceState");
  window.addEventListener("popstate", notify);
  window.addEventListener("hashchange", notify);
  [400, 1500].forEach((ms) => setTimeout(notify, ms));
}

function injectUi() {
  if (document.getElementById("cs-sidebar")) {
    schedulePageSync();
    return;
  }
  const style = document.createElement("style");
  style.id = "cs-style";
  style.textContent = `
    #cs-fab { position:fixed; right:24px; bottom:24px; z-index:2147483000; background:#0f766e; color:#fff; border:0; border-radius:999px; padding:12px 16px; font:600 14px Segoe UI,sans-serif; cursor:pointer; box-shadow:0 8px 24px rgba(15,118,110,.35); display:flex; align-items:center; gap:8px; }
    #cs-fab img { width:22px; height:22px; border-radius:6px; }
    #cs-sidebar { position:fixed; top:0; right:0; width:380px; max-width:100%; height:100vh; background:#fff; z-index:2147483001; box-shadow:-8px 0 30px rgba(15,23,42,.2); transform:translateX(110%); transition:transform .2s; font-family:Segoe UI,sans-serif; color:#0f172a; }
    #cs-sidebar.open { transform:none; }
    #cs-sidebar header { padding:14px 16px; background:linear-gradient(135deg,#0f766e,#134e4a); color:#fff; display:flex; justify-content:space-between; align-items:center; gap:10px; }
    #cs-sidebar header .brand { display:flex; align-items:center; gap:10px; }
    #cs-sidebar header .brand img { width:32px; height:32px; border-radius:9px; background:#fff; }
    #cs-sidebar header .brand small { display:block; opacity:.8; font-weight:500; font-size:11px; }
    #cs-sidebar header #cs-close { width:auto; margin:0; background:transparent; color:#fff; border-color:rgba(255,255,255,.25); }
    #cs-sidebar header .cs-head-actions { display:flex; align-items:center; gap:6px; flex-shrink:0; }
    #cs-sidebar header .cs-locale-wrap { display:flex; align-items:center; height:32px; padding:0 4px 0 8px; background:#fff; border:1px solid rgba(255,255,255,.85); border-radius:999px; box-shadow:0 1px 2px rgba(15,23,42,.18); }
    #cs-sidebar header .cs-locale-wrap:hover, #cs-sidebar header .cs-locale-wrap:focus-within { box-shadow:0 0 0 2px rgba(255,255,255,.28), 0 1px 2px rgba(15,23,42,.18); }
    #cs-sidebar header .cs-locale-wrap::before { content:"Aअ"; display:flex; align-items:center; justify-content:center; margin-right:4px; font-size:10px; font-weight:800; letter-spacing:-.05em; color:#0f766e; line-height:1; }
    #cs-sidebar header #cs-locale { -webkit-appearance:none; appearance:none; width:auto !important; max-width:7.2rem; min-width:4.6rem; margin:0 !important; padding:4px 22px 4px 2px !important; font:600 12px/1.2 Segoe UI,sans-serif; color:#134e4a !important; background-color:#fff !important; background-image:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='none' stroke='%230f766e' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round' d='M3 4.5 6 7.5 9 4.5'/%3E%3C/svg%3E"); background-repeat:no-repeat; background-position:right 4px center; border:0 !important; border-radius:0 !important; box-shadow:none; cursor:pointer; color-scheme:light; }
    #cs-sidebar header #cs-locale:focus { outline:none; }
    #cs-sidebar header #cs-locale option { color:#0f172a; background:#fff; }
    #cs-sidebar .body { padding:16px; overflow:auto; height:calc(100vh - 64px); }
    #cs-sidebar input, #cs-sidebar button, #cs-sidebar select, #cs-sidebar textarea { width:100%; margin:6px 0; padding:8px; border-radius:10px; border:1px solid #e2e8f0; box-sizing:border-box; }
    #cs-sidebar .btn { background:#0f766e; color:#fff; border:0; font-weight:600; cursor:pointer; }
    #cs-sidebar .btn:disabled { opacity:.55; cursor:not-allowed; }
    #cs-sidebar .btn.busy { cursor:wait; }
    #cs-sidebar .btn-fill { background:#15803d; }
    #cs-sidebar .ghost { background:#f8fafc; }
    #cs-sidebar .cs-sizes { display:flex; flex-wrap:wrap; gap:6px; margin:8px 0; }
    #cs-sidebar .cs-size { width:auto; margin:0; padding:6px 12px; border:1px solid #e2e8f0; border-radius:8px; background:#fff; font-weight:600; cursor:pointer; color:#0f172a; }
    #cs-sidebar .cs-size.active { background:#0f766e; color:#fff; border-color:#0f766e; }
    #cs-sidebar #cs-progress { font-size:12px; color:#0f766e; min-height:18px; }
    #cs-sidebar #cs-progress.error { color:#b91c1c; font-weight:600; }
    #cs-sidebar .cs-card { border:1px solid #e2e8f0; border-radius:12px; padding:10px; margin:8px 0; cursor:pointer; }
    #cs-sidebar .cs-card.active { border-color:#0f766e; }
    #cs-sidebar .cs-note { font-size:12px; color:#64748b; }
    #cs-sidebar .cs-ok { background:#ecfdf5; border:1px solid #a7f3d0; color:#065f46; border-radius:10px; padding:8px 10px; font-size:13px; }
    #cs-sidebar .cs-store { display:flex; align-items:center; gap:8px; background:#ecfdf5; border:1px solid #a7f3d0; color:#065f46; border-radius:999px; padding:6px 12px; font-size:13px; font-weight:600; width:fit-content; }
    #cs-sidebar .cs-store::before { content:"\\2713"; }
    #cs-sidebar .cs-status { display:flex; align-items:center; gap:6px; font-size:13px; font-weight:600; }
    #cs-sidebar .cs-status.ok::before { content:""; width:8px; height:8px; border-radius:99px; background:#16a34a; display:inline-block; }
    #cs-sidebar .cs-thumb { width:100%; height:140px; object-fit:cover; border-radius:10px; background:#f1f5f9; display:none; }
    #cs-sidebar .cs-drop { border:2px dashed #94a3b8; border-radius:12px; padding:18px 12px; text-align:center; cursor:pointer; background:#f8fafc; font-size:13px; color:#334155; }
    #cs-sidebar .cs-drop.drag { border-color:#0f766e; background:#ecfdf5; }
    #cs-sidebar .cs-attr { display:flex; justify-content:space-between; gap:8px; font-size:13px; padding:4px 0; border-bottom:1px solid #f1f5f9; }
    #cs-sidebar .cs-choice { border:1px solid #e2e8f0; border-radius:10px; padding:8px; margin:6px 0; cursor:pointer; font-size:13px; }
    #cs-sidebar .cs-choice.active { border-color:#0f766e; background:#ecfdf5; }
    #cs-sidebar .cs-row { display:grid; grid-template-columns:1fr 1fr 1fr; gap:8px 6px; align-items:end; }
    #cs-sidebar .cs-row input { margin-top:0; }
    #cs-sidebar .cs-field { display:flex; flex-direction:column; gap:4px; min-width:0; margin:0; font-weight:600; color:#334155; }
    #cs-sidebar .cs-field > span { font-size:11px; line-height:1.25; }
    #cs-sidebar .cs-field input { margin:0; font-weight:500; }
    #cs-sidebar .cs-meter { display:flex; align-items:center; gap:8px; font-size:12px; margin:8px 0 0; }
    #cs-sidebar .cs-meter .bar { flex:1; height:8px; background:#e2e8f0; border-radius:999px; overflow:hidden; }
    #cs-sidebar .cs-meter .bar i { display:block; height:100%; width:0; background:linear-gradient(90deg,#dc2626,#16a34a); }
    #cs-sidebar .footer-actions { position:sticky; bottom:0; background:#fff; padding-top:10px; border-top:1px solid #f1f5f9; }
    #cs-sidebar details { margin:10px 0; }
    #cs-sidebar summary { cursor:pointer; color:#334155; font-weight:600; }
  `;
  document.documentElement.appendChild(style);

  const fab = document.createElement("button");
  fab.id = "cs-fab";
  fab.innerHTML = `${logoImg(22)} Catalog Studio`;
  const sidebar = document.createElement("aside");
  sidebar.id = "cs-sidebar";
  sidebar.innerHTML = `
    <header>
      <div class="brand">${logoImg(32)}<div><strong>Catalog Studio</strong><small data-i18n="ext.autofill">Auto-Fill v1.4.1</small></div></div>
      <div class="cs-head-actions">
        <label class="cs-locale-wrap"><select id="cs-locale" aria-label="Language">${localeOptionsHtml()}</select></label>
        <button id="cs-close" data-i18n="ext.close">Close</button>
      </div>
    </header>
    <div class="body">
      <section>
        <p id="cs-user" class="cs-status">Checking pairing...</p>
        <div class="cs-meter"><span id="cs-meter-done">0 done</span><div class="bar"><i id="cs-meter-bar"></i></div><span id="cs-meter-left">-</span></div>
        <p id="cs-quota" class="cs-note"></p>
        <p id="cs-shop-lock" class="cs-note"></p>
        <p id="cs-store" class="cs-store" style="display:none"></p>
        <p id="cs-ai" class="cs-note"></p>
        <p id="cs-account-note" class="cs-note"></p>
        <button class="ghost" id="cs-disconnect" data-i18n="ext.disconnect">Disconnect</button>
      </section>
      <section>
        <h3 data-i18n="ext.thisPage">This page</h3>
        <p id="cs-page" class="cs-note">Detecting page...</p>
      </section>
      <section>
        <h3 data-i18n="ext.category">Category</h3>
        <input id="cs-category" readonly data-i18n-placeholder="ext.catPh" placeholder="Detected from Meesho after you select a category" />
        <input id="cs-cat-search" data-i18n-placeholder="ext.catSearch" placeholder="Search Catalog Studio templates" />
        <div id="cs-cat-results"></div>
        <p id="cs-category-note" class="cs-note" data-i18n="ext.catNote">Catalog Studio reads the category Meesho already selected.</p>
      </section>
      <section>
        <h3 data-i18n="ext.image">Product image</h3>
        <p id="cs-image-status" class="cs-note">Add a photo on the Meesho form (Product 1 / Front View). Catalog Studio will pick it automatically.</p>
        <img id="cs-image" class="cs-thumb" alt="Product preview" />
        <button class="ghost" id="cs-recapture" data-i18n="ext.recapture">Capture image from this page again</button>
        <div id="cs-drop" class="cs-drop" data-i18n="ext.drop">Or drop an image here / click to choose</div>
        <input id="cs-file" type="file" accept="image/jpeg,image/png,image/webp" hidden />
      </section>
      <section>
        <h3 data-i18n="ext.info">Product info</h3>
        <p class="cs-note" data-i18n="ext.infoNote">The more you write, the more accurate the listing. Catalog Studio uses this with the photo.</p>
        <textarea id="cs-notes" rows="4" data-i18n-placeholder="ext.notesPh" placeholder="Fabric, color, single piece or set, occasion"></textarea>
        <input id="cs-style" data-i18n-placeholder="ext.stylePh" placeholder="Style code / Product ID (optional)" />
        <p class="cs-note" data-i18n="ext.gstNote">GST, HSN, weight, and manufacturer details are filled automatically from this Meesho store and your Catalog Studio profile. Edit only if you need to override.</p>
        <details id="cs-settings">
          <summary data-i18n="ext.sellerSettings">Seller settings</summary>
          <p class="cs-note" data-i18n="ext.sellerNote">These defaults save to your Catalog Studio account and apply on every new listing.</p>
          <p class="cs-note" data-i18n="ext.sellerPriceHint">Return cut (₹) is rupees kept aside for returns. MRP multiplier sets MRP = selling price × this number (1.9 means ₹100 selling → ₹190 MRP). Default inventory is the stock filled when this listing's inventory is empty.</p>
          <div class="cs-row">
            <label class="cs-field"><span data-i18n="ext.retcut">Return cut (₹)</span><input id="cs-retcut" type="text" inputmode="decimal" /></label>
            <label class="cs-field"><span data-i18n="ext.mrpmul">MRP multiplier</span><input id="cs-mrpmul" type="text" inputmode="decimal" /></label>
            <label class="cs-field"><span data-i18n="ext.inv">Default inventory</span><input id="cs-set-inv" type="text" inputmode="numeric" /></label>
          </div>
          <p class="cs-note" data-i18n="ext.sellerPackHint">Box size in centimetres (length × width × height). Pack weight is the packed parcel in grams — not the product weight below.</p>
          <div class="cs-row">
            <label class="cs-field"><span data-i18n="ext.packL">Length (cm)</span><input id="cs-pk-l" type="text" inputmode="decimal" /></label>
            <label class="cs-field"><span data-i18n="ext.packW">Width (cm)</span><input id="cs-pk-w" type="text" inputmode="decimal" /></label>
            <label class="cs-field"><span data-i18n="ext.packH">Height (cm)</span><input id="cs-pk-h" type="text" inputmode="decimal" /></label>
          </div>
          <label class="cs-field"><span data-i18n="ext.packWt">Pack weight (g)</span><input id="cs-pk-wt" type="text" inputmode="decimal" /></label>
          <label class="cs-field"><span data-i18n="ext.keywords">Top keywords (comma separated)</span><input id="cs-keywords" /></label>
          <button class="ghost" id="cs-save-settings" type="button" data-i18n="ext.saveSettings">Save settings</button>
        </details>
        <p class="cs-note" data-i18n="ext.listingHead">This listing — GST %, HSN, product weight, and prices. Empty price uses seller settings.</p>
        <div class="cs-row">
          <label class="cs-field"><span data-i18n="ext.gst">GST (%)</span><input id="cs-gst" type="text" inputmode="decimal" /></label>
          <label class="cs-field"><span data-i18n="ext.hsn">HSN code</span><input id="cs-hsn" type="text" inputmode="numeric" /></label>
          <label class="cs-field"><span data-i18n="ext.weight">Product weight (g)</span><input id="cs-weight" type="text" inputmode="decimal" /></label>
        </div>
        <div class="cs-row">
          <label class="cs-field"><span data-i18n="ext.mrp">MRP (₹)</span><input id="cs-mrp" type="text" inputmode="decimal" /></label>
          <label class="cs-field"><span data-i18n="ext.price">Selling price (₹)</span><input id="cs-price" type="text" inputmode="decimal" /></label>
          <label class="cs-field"><span data-i18n="ext.inventory">Inventory</span><input id="cs-inventory" type="text" inputmode="numeric" /></label>
        </div>
        <label class="cs-field"><span data-i18n="ext.addr">Manufacturer / packer address</span><input id="cs-address" /></label>
        <label class="cs-field"><span data-i18n="ext.pincode">Pincode</span><input id="cs-pincode" type="text" inputmode="numeric" /></label>
      </section>
      <section>
        <h3 data-i18n="ext.sizes">Sizes from Meesho</h3>
        <p id="cs-size-note" class="cs-note" data-i18n="ext.sizeNote">Sizes you pick on Meesho show here automatically.</p>
        <div id="cs-sizes" class="cs-sizes"></div>
      </section>
      <section class="footer-actions">
        <button class="btn" id="cs-generate" type="button" data-i18n="ext.generate">Generate</button>
        <button class="btn btn-fill" id="cs-fill" style="display:none" data-i18n="ext.fill">Fill Values for Form</button>
        <button class="ghost" id="cs-reset-listing" data-i18n="ext.reset">Reset / new listing</button>
        <p id="cs-progress" class="cs-progress"></p>
      </section>
      <section id="cs-generated" style="display:none">
        <h3 data-i18n="ext.details">Product details</h3>
        <div id="cs-attrs"></div>
        <h3 data-i18n="ext.title">Title</h3>
        <div id="cs-titles"></div>
        <h3 data-i18n="ext.desc">Description</h3>
        <div id="cs-descs"></div>
      </section>
      <details>
        <summary data-i18n="ext.saved">Saved products and extra autofill</summary>
        <input id="cs-search" data-i18n-placeholder="ext.searchProd" placeholder="Search saved products" />
        <div id="cs-products"></div>
        <p class="cs-note" data-i18n="ext.reviewNote">Review every field. Fill only types into the Meesho form after you click it. Catalog Studio never submits and never adds buttons to Meesho.</p>
        <button class="ghost" data-mode="all" data-i18n="ext.fillAll">Autofill all fields</button>
        <button class="ghost" data-mode="basic" data-i18n="ext.fillBasic">Autofill basic details</button>
        <button class="ghost" data-mode="attributes" data-i18n="ext.fillAttr">Autofill attributes</button>
        <button class="ghost" id="cs-stop" data-i18n="ext.stop">Stop autofill</button>
      </details>
    </div>
  `;
  document.body.append(fab, sidebar);
  fab.onclick = () => openSidebarByUser();
  sidebar.querySelector("#cs-close")?.addEventListener("click", () => closeSidebarByUser());
  sidebar.querySelector("#cs-stop")?.addEventListener("click", () => {
    stopAutofill();
    setProgress(t("ext.stopped"));
  });
  sidebar.querySelector("#cs-disconnect")?.addEventListener("click", async () => {
    await sendRuntimeMessage({ type: "UNPAIR" });
    void renderAccount();
    void loadProducts("");
  });
  sidebar.querySelector("#cs-search")?.addEventListener("input", (e) => loadProducts((e.target as HTMLInputElement).value));
  bindImageUpload(sidebar);
  sidebar.querySelector("#cs-recapture")?.addEventListener("click", recaptureFromPage);
  sidebar.querySelector("#cs-generate")?.addEventListener("click", analyzePickedImage);
  sidebar.querySelector("#cs-fill")?.addEventListener("click", () => runAutofill("all"));
  sidebar.querySelector("#cs-reset-listing")?.addEventListener("click", resetListing);
  sidebar.querySelector("#cs-save-settings")?.addEventListener("click", () => void saveSellerSettings());
  sidebar.querySelector("#cs-cat-search")?.addEventListener("input", (e) => renderCategoryResults((e.target as HTMLInputElement).value));
  sidebar.querySelectorAll("[data-mode]").forEach((btn) =>
    btn.addEventListener("click", () => runAutofill((btn as HTMLElement).dataset.mode || "all")),
  );
  ["cs-gst", "cs-hsn", "cs-weight", "cs-style", "cs-pincode", "cs-address", "cs-mrp", "cs-price", "cs-inventory"].forEach((id) => {
    sidebar.querySelector(`#${id}`)?.addEventListener("change", persistListingDefaults);
  });
  watchStorageChanges((changes, area) => {
    if (area !== "local") return;
    if (changes.csLocale) {
      void loadLocale().then(() => applyOverlayLocale());
    }
    if (changes.pairingKey || changes.user || changes.business) {
      void renderAccount();
      void loadProducts("");
      void loadBusinessAndProfile();
    }
  });
  sidebar.querySelector("#cs-locale")?.addEventListener("change", (event) => {
    setLocale((event.target as HTMLSelectElement).value);
    applyOverlayLocale();
  });
  window.setTimeout(() => {
    void initSidebarState(sidebar);
  }, 0);
}

async function initSidebarState(sidebar: HTMLElement) {
  try {
    await loadLocale();
    applyOverlayLocale();
    await renderAccount();
    await loadProducts("");
    await loadBusinessAndProfile();
    syncPageHint();
    syncCategoryFromPage();
    syncStoreFromPage();
    await restoreListingDefaults();
    await loadExtensionFeatures();
    maybeAutoOpenSidebar();
  } catch (error) {
    if (!isInvalidatedContext(error)) {
      console.warn("Catalog Studio UI init failed", error);
    }
  }
}

function applyOverlayLocale() {
  const sidebar = document.getElementById("cs-sidebar");
  if (sidebar) applyStaticI18n(sidebar);
  const select = document.getElementById("cs-locale") as HTMLSelectElement | null;
  if (select) {
    select.setAttribute("aria-label", t("lang.label"));
    select.innerHTML = localeOptionsHtml();
    select.value = currentLocale();
  }
  const genBtn = document.getElementById("cs-generate") as HTMLButtonElement | null;
  if (genBtn && genBtn.dataset.busy !== "1") genBtn.textContent = t("ext.generate");
  const fillBtn = document.getElementById("cs-fill") as HTMLButtonElement | null;
  if (fillBtn) fillBtn.textContent = fillButtonLabel();
  if (!pickedFile) {
    const status = document.getElementById("cs-image-status");
    if (status && !status.classList.contains("cs-ok")) status.textContent = t("ext.imageNote");
  }
  syncPageHint();
  void renderAccount();
  paintQuota();
}

function syncPageHint() {
  const el = document.getElementById("cs-page");
  if (!el) return;
  if (isMeeshoCatalogListPage() && !isMeeshoAddCatalogFlow()) {
    el.textContent = t("ext.pageList");
  } else if (isMeeshoBulkTemplateStep()) {
    el.textContent = t("ext.pageBulkTpl");
  } else if (isMeeshoBulkCatalogPage()) {
    el.textContent = t("ext.pageBulk");
  } else if (isMeeshoCategoryPickerVisible() || /select category/i.test(document.title)) {
    el.textContent = t("ext.pageCat");
  } else if (isMeeshoProductDetailsPage()) {
    el.textContent = pickedFile ? t("ext.pageProductGen") : t("ext.pageProduct");
  } else if (isMeeshoAddCatalogFlow()) {
    el.textContent = t("ext.pageAdd");
  } else if (isMeeshoCatalogPage()) {
    el.textContent = t("ext.pageCatalog");
  } else if (detectMarketplace() === "MEESHO") {
    el.textContent = t("ext.pageSupplier");
  } else {
    el.textContent = t("ext.pageOther", { name: detectMarketplace() });
  }
}

function syncCategoryFromPage() {
  const path = readMeeshoCategoryPath();
  const leaf = path[path.length - 1] || "";
  const input = document.getElementById("cs-category") as HTMLInputElement | null;
  const note = document.getElementById("cs-category-note");
  if (!input) return;
  if (leaf) {
    input.value = leaf;
    applyCategoryDefaults(leaf);
    if (note) {
      note.className = "cs-ok";
      note.textContent = t("ext.catSelected", { leaf, path: path.join(" / ") });
    }
    return;
  }
  input.value = "";
  if (note) {
    note.className = "cs-note";
    note.textContent = t("ext.catHint");
  }
}

function fillButtonLabel() {
  return detectMarketplace() === "MEESHO" ? t("ext.fill") : t("ext.fillMarket");
}

function syncSizesFromPage() {
  const pageSizes = detectPageSizes();
  const pageSelected = detectSelectedPageSizes();
  const listing = {
    title: generated?.titles[generated.selectedTitle] || String(selected?.name || ""),
    genericName: readMeeshoCategoryFromPage() || generated?.genericName || "",
    mainCategory: readMeeshoCategoryFromPage(),
    size: pageSelected[0] || "",
  };
  availableSizes = pageSizes.length ? pageSizes : pageSelected.length ? pageSelected : fallbackSizesForListing(listing);
  if (availableSizes.length === 1 && /semi stitched|free size/i.test(availableSizes[0]) && !pageSelected.length) {
    selectedSizes = new Set(availableSizes);
  } else {
    selectedSizes = new Set(pageSelected);
  }
  const key = `${availableSizes.join("|")}::${[...selectedSizes].join("|")}`;
  if (key === lastSizeKey) {
    syncGenerateEnabled();
    return;
  }
  lastSizeKey = key;
  renderSizeOptions();
  syncGenerateEnabled();
  if (pageSelected.length) {
    const note = document.getElementById("cs-progress");
    if (note && !note.classList.contains("error") && /select a size/i.test(note.textContent || "")) {
      setProgress(`Meesho size ${pageSelected.join(", ")} detected. Click Generate.`);
    }
  }
}

function renderSizeOptions() {
  const box = document.getElementById("cs-sizes");
  const note = document.getElementById("cs-size-note");
  if (!box) return;
  if (!availableSizes.length) {
    box.innerHTML = `<p class="cs-note">${t("ext.waitingSizes")}</p>`;
    if (note) {
      note.className = "cs-note";
      note.textContent = t("ext.sizeNote");
    }
    return;
  }
  box.innerHTML = availableSizes
    .map(
      (size) =>
        `<button type="button" class="cs-size ${selectedSizes.has(size) ? "active" : ""}" data-size="${escapeHtml(size)}">${escapeHtml(size)}</button>`,
    )
    .join("");
  box.querySelectorAll<HTMLButtonElement>("[data-size]").forEach((btn) => {
    btn.addEventListener("click", () => void toggleSize(btn.dataset.size || ""));
  });
  if (note) {
    note.className = selectedSizes.size ? "cs-ok" : "cs-note";
    note.textContent = selectedSizes.size
      ? `Meesho size ${[...selectedSizes].join(", ")} selected.`
      : "Sizes you pick on Meesho show here automatically.";
  }
}

async function toggleSize(size: string) {
  if (!size) return;
  if (selectedSizes.has(size)) selectedSizes.delete(size);
  else selectedSizes.add(size);
  renderSizeOptions();
  setProgress(`Size ${size} marked in Catalog Studio only. Select the same size on Meesho yourself.`);
}

function syncStoreFromPage() {
  detectedStore = detectMeeshoStore();
  void showStoreName();
  void verifyShopLock();
}

async function showStoreName() {
  const stored = await storageGet(["business", "user", "meeshoStore"]);
  if (stored.business) businessProfile = stored.business;
  if (detectedStore?.name) {
    void storageSet({ meeshoStore: detectedStore });
  } else if (sanitizeStoreName(stored.meeshoStore?.name || "")) {
    detectedStore = { ...stored.meeshoStore, name: sanitizeStoreName(stored.meeshoStore.name) };
  } else {
    const fallback = sanitizeStoreName(businessProfile?.name || stored.user?.workspace || stored.user?.name || "");
    if (fallback) detectedStore = { name: fallback, source: "storage" };
  }
  const el = document.getElementById("cs-store");
  if (!el) return;
  if (detectedStore?.name) {
    el.style.display = "flex";
    el.textContent = detectedStore.name;
  } else {
    el.style.display = "none";
    el.textContent = "";
  }
  void renderAccount();
}

function applyCategoryDefaults(category: string) {
  const notes = (document.getElementById("cs-notes") as HTMLTextAreaElement | null)?.value || "";
  const defaults = defaultsForCategory(category, notes);
  const gst = document.getElementById("cs-gst") as HTMLInputElement | null;
  const hsn = document.getElementById("cs-hsn") as HTMLInputElement | null;
  const weight = document.getElementById("cs-weight") as HTMLInputElement | null;
  if (gst && !gst.value) gst.value = defaults.gst;
  if (hsn && !hsn.value) hsn.value = defaults.hsn;
  if (weight && !weight.value) weight.value = defaults.netWeight;
}

async function renderAccount() {
  const session = await getSession();
  const el = document.getElementById("cs-user");
  if (!el) return;
  const note = document.getElementById("cs-account-note");
  const ai = document.getElementById("cs-ai");
  if (!session) {
    el.className = "cs-status";
    el.textContent = t("ext.notPaired");
    if (note) note.textContent = t("ext.needPair");
    if (ai) ai.textContent = "";
    return;
  }
  el.className = "cs-status ok";
  el.textContent = t("ext.connected");
  if (ai) ai.textContent = t("ext.aiReady");
  if (note) {
    note.textContent = detectedStore?.name
      ? t("ext.storeConn", { name: detectedStore.name })
      : t("ext.pairedAs", { name: session.user?.workspace || session.user?.name || "Catalog Studio" });
  }
}

async function loadBusinessAndProfile() {
  const session = await getSession();
  if (!session) return;
  const stored = await storageGet(["business", "user"]);
  businessProfile = stored.business || {
    name: stored.user?.workspace || stored.user?.name,
  };
  if (businessProfile?.address && !inputValue("cs-address")) {
    setInput("cs-address", businessProfile.address);
  }
  if (businessProfile?.address && !inputValue("cs-pincode")) {
    setInput("cs-pincode", extractPincode(businessProfile.address));
  }
  try {
    const data = await sendRuntimeMessage<{ data?: { marketplace?: string; profileJson?: Record<string, string> }[] }>({ type: "API", path: "/extension/profiles" });
    const profiles = (data?.data || []) as { marketplace?: string; profileJson?: Record<string, string> }[];
    const meesho = profiles.find((item) => (item.marketplace || "").toUpperCase() === "MEESHO") || profiles[0];
    const json = meesho?.profileJson || {};
    if (json.gst) setInput("cs-gst", json.gst);
    if (json.hsn) setInput("cs-hsn", json.hsn);
    if (json.netWeight) setInput("cs-weight", json.netWeight);
    if (json.address || json.manufacturerAddress) setInput("cs-address", json.address || json.manufacturerAddress);
    if (json.pincode || json.packerPincode) setInput("cs-pincode", json.pincode || json.packerPincode);
    persistListingDefaults();
  } catch {
    // profiles are optional
  }
}

function bindImageUpload(sidebar: HTMLElement) {
  const drop = sidebar.querySelector("#cs-drop") as HTMLElement;
  const input = sidebar.querySelector("#cs-file") as HTMLInputElement;
  drop.addEventListener("click", () => input.click());
  drop.addEventListener("dragover", (event) => {
    event.preventDefault();
    drop.classList.add("drag");
  });
  drop.addEventListener("dragleave", () => drop.classList.remove("drag"));
  drop.addEventListener("drop", (event) => {
    event.preventDefault();
    drop.classList.remove("drag");
    const file = event.dataTransfer?.files?.[0];
    if (file) previewLocalImage(file, "user-drop");
  });
  input.addEventListener("change", () => {
    const file = input.files?.[0];
    if (file) previewLocalImage(file, "user-drop");
  });
}

function previewLocalImage(file: File, source: "page" | "user-drop", pageSource?: PageImageSource) {
  pickedFile = file;
  pickedSource = source;
  const img = document.getElementById("cs-image") as HTMLImageElement | null;
  if (img) {
    img.src = URL.createObjectURL(file);
    img.style.display = "block";
  }
  const status = document.getElementById("cs-image-status");
  if (status) {
    status.className = "cs-ok";
    status.textContent =
      source === "page"
        ? pageSource === "page-upload"
          ? t("ext.imgForm")
          : t("ext.imgPage")
        : t("ext.imgReady", { name: file.name });
  }
  setProgress(t("ext.imgReadyGen"));
}

async function recaptureFromPage() {
  setProgress(t("ext.lookingFront"));
  const file = await captureBestPageImage();
  if (!file) {
    setProgress(t("ext.noImage"));
    return;
  }
  previewLocalImage(file, "page", "recapture");
}

async function analyzePickedImage() {
  const btn = document.getElementById("cs-generate") as HTMLButtonElement | null;
  if (btn?.dataset.busy === "1") return;
  if (btn) {
    btn.dataset.busy = "1";
    btn.disabled = true;
    btn.classList.add("busy");
    btn.textContent = t("ext.generating");
  }
  try {
    if (!pickedFile) {
      setProgress(t("ext.lookingFront"));
      const file = await captureBestPageImage();
      if (file) previewLocalImage(file, "page", "recapture");
    }
    if (!pickedFile) {
      setProgress(t("ext.addFront"), true);
      return;
    }
    const session = await getSession();
    if (!session) {
      setProgress(t("ext.pairThenGen"), true);
      void renderAccount();
      return;
    }
    setProgress(t("ext.genFromImage"));
    persistListingDefaults();
    const notes = (document.getElementById("cs-notes") as HTMLTextAreaElement | null)?.value || "";
    const keywords = String(sellerSettings.keywords || []).length
      ? ` Keywords: ${(sellerSettings.keywords as string[]).join(", ")}.`
      : "";
    const categoryLeaf = readMeeshoCategoryFromPage();
    const categoryPath = readMeeshoCategoryPath().join(" / ");
    const pageCategory = categoryPath || categoryLeaf;
    if (shopLock === "bad" && !isMeeshoPageChrome(shopLockMessage)) {
      setProgress(shopLockMessage || t("ext.shopMismatch"), true);
      return;
    }
    const base64 = await fileToBase64(pickedFile);
    const data = await sendRuntimeMessage<{ error?: string; data?: Record<string, unknown> }>({
      type: "ANALYZE",
      filename: pickedFile.name,
      contentType: pickedFile.type || "image/jpeg",
      base64,
      marketplace: detectMarketplace() === "UNKNOWN" ? "MEESHO" : detectMarketplace(),
      notes: `${notes}${keywords}`.trim(),
      categoryHint: pageCategory,
      meeshoName: sanitizeStoreName(detectedStore?.name || ""),
      meeshoUid: meeshoUid(),
    });
    if (!data) {
      setProgress("Extension was reloaded. Close this tab and open the listing page again, then click Generate.", true);
      return;
    }
    if (data?.error) {
      if (/revoked|invalid extension key|unauthorized/i.test(data.error)) {
        await sendRuntimeMessage({ type: "UNPAIR" });
        setProgress("Pairing expired. Open the Catalog Studio dashboard to reconnect.", true);
        void renderAccount();
        return;
      }
      setProgress(data.error, true);
      return;
    }
    const payload = data?.data || {};
    const product = payload.product || {};
    const split = splitPattern(product.pattern, notes);
    const combo = parseCombo(notes);
    generated = {
      productId: payload.productId,
      productType: product.productType,
      category: product.category,
      subCategory: product.subCategory,
      gender: product.gender,
      primaryColor: product.primaryColor,
      pattern: split.pattern,
      printType: split.printType,
      material: product.material,
      sleeveType: product.sleeveType,
      neckType: product.neckType,
      fit: product.fit,
      occasion: product.occasion,
      style: product.style,
      comboOf: combo.label,
      netQuantity: combo.quantity,
      ornamentation: detectOrnamentation(notes, product.pattern, product.productDescription, product.style),
      genericName: categoryLeaf || product.productType || product.subCategory,
      titles: uniqueTexts(product.suggestedTitles || []),
      descriptions: uniqueTexts([...(product.suggestedDescriptions || []), product.productDescription]),
      selectedTitle: 0,
      selectedDesc: 0,
    };
    await applyFillGaps(pageCategory, notes);
    renderGenerated();
    const fillBtn = document.getElementById("cs-fill") as HTMLButtonElement | null;
    if (fillBtn) {
      fillBtn.style.display = "block";
      fillBtn.textContent = fillButtonLabel();
    }
    syncCategoryFromPage();
    syncSizesFromPage();
    const productId = payload.productId;
    if (productId) {
      await loadProducts("");
      try {
        await selectProduct(String(productId));
      } catch {
        // still fill the form with generated details
      }
    }
    setProgress("Listing generated. Review in Catalog Studio, then click Fill Values for Form. Catalog Studio will not click or change the Meesho page until you do.");
    return;
  } catch (error) {
    const message = error instanceof Error ? error.message : "Generate failed";
    setProgress(
      /failed to fetch|networkerror|load failed/i.test(message)
        ? `Cannot reach Catalog Studio API at ${DEFAULT_API_BASE}. Check the connection, then click Generate again.`
        : message,
      true,
    );
  } finally {
    if (btn) {
      btn.dataset.busy = "0";
      btn.textContent = t("ext.generate");
      syncGenerateEnabled();
    }
  }
}

function renderGenerated() {
  const panel = document.getElementById("cs-generated");
  if (!panel || !generated) return;
  panel.style.display = "block";
  const attrs = [
    ["Gender", generated.gender],
    ["Color", generated.primaryColor],
    ["Combo of", generated.comboOf],
    ["Fabric", generated.material],
    ["Generic Name", generated.genericName],
    ["Net Quantity (N)", generated.netQuantity],
    ["Pattern", generated.pattern],
    ["Print or Pattern Type", generated.printType],
    ["Sleeve", generated.sleeveType],
    ["Fit", generated.fit],
    ["Country of Origin", "India"],
    ["Manufacturer", detectedStore?.name || businessProfile?.name || ""],
    ["Ornamentation", generated.ornamentation],
    ["Sizes", [...selectedSizes].join(", ")],
  ].filter(([, value]) => value);
  document.getElementById("cs-attrs")!.innerHTML = attrs
    .map(([label, value]) => `<div class="cs-attr"><span>${escapeHtml(label)}</span><strong>${escapeHtml(String(value))}</strong></div>`)
    .join("");
  document.getElementById("cs-titles")!.innerHTML = generated.titles
    .map(
      (title, index) =>
        `<div class="cs-choice ${index === generated!.selectedTitle ? "active" : ""}" data-title="${index}">${escapeHtml(title)} <span class="cs-note">${title.length} chars</span></div>`,
    )
    .join("") || `<p class="cs-note">No titles yet.</p>`;
  document.getElementById("cs-descs")!.innerHTML = generated.descriptions
    .map(
      (text, index) =>
        `<div class="cs-choice ${index === generated!.selectedDesc ? "active" : ""}" data-desc="${index}">${escapeHtml(text.slice(0, 280))}${text.length > 280 ? "..." : ""} <span class="cs-note">${text.length} chars</span></div>`,
    )
    .join("") || `<p class="cs-note">No descriptions yet.</p>`;
  document.querySelectorAll<HTMLElement>("[data-title]").forEach((el) => {
    el.addEventListener("click", () => {
      if (!generated) return;
      generated.selectedTitle = Number(el.dataset.title);
      renderGenerated();
    });
  });
  document.querySelectorAll<HTMLElement>("[data-desc]").forEach((el) => {
    el.addEventListener("click", () => {
      if (!generated) return;
      generated.selectedDesc = Number(el.dataset.desc);
      renderGenerated();
    });
  });
}

function resetListing() {
  generated = null;
  selected = null;
  pickedFile = null;
  pickedSource = "";
  lastSizeKey = "";
  const img = document.getElementById("cs-image") as HTMLImageElement | null;
  if (img) {
    img.removeAttribute("src");
    img.style.display = "none";
  }
  const status = document.getElementById("cs-image-status");
  if (status) {
    status.className = "cs-note";
    status.textContent = t("ext.needPhoto");
  }
  const generatedEl = document.getElementById("cs-generated");
  if (generatedEl) generatedEl.style.display = "none";
  const fillBtn = document.getElementById("cs-fill") as HTMLButtonElement | null;
  if (fillBtn) {
    fillBtn.style.display = "none";
    fillBtn.textContent = fillButtonLabel();
  }
  syncSizesFromPage();
  setMeter(0, 0);
  setProgress(t("ext.resetOk"));
}

function fileToBase64(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || "");
      resolve(result.includes(",") ? result.split(",")[1] : result);
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

async function loadProducts(q: string) {
  const box = document.getElementById("cs-products")!;
  try {
    const session = await getSession();
    if (!session) {
      box.textContent = t("ext.pairFirst");
      return;
    }
    const data = await sendRuntimeMessage<{ error?: string; data?: ProductRecord[] }>({ type: "API", path: `/extension/products?q=${encodeURIComponent(q)}` });
    if (!data) {
      box.textContent = t("ext.pairFirst");
      return;
    }
    if (data?.error) throw new Error(data.error);
    const products = (data.data || []) as ProductRecord[];
    box.innerHTML = products
      .map(
        (p) =>
          `<div class="cs-card" data-id="${p.id}"><strong>${escapeHtml(String(p.name || t("ext.untitled")))}</strong><div>${escapeHtml(String(p.productType || p.category || ""))} · ${escapeHtml(String(p.primaryColor || ""))}</div></div>`,
      )
      .join("");
    box.querySelectorAll(".cs-card").forEach((card) =>
      card.addEventListener("click", () => selectProduct((card as HTMLElement).dataset.id!)),
    );
  } catch {
    box.textContent = t("ext.loadFail");
  }
}

async function selectProduct(id: string) {
  const session = await getSession();
  if (!session) return;
  const data = await sendRuntimeMessage<{ error?: string; data?: ProductRecord }>({ type: "API", path: `/extension/products/${id}` });
  if (!data) return;
  if (data?.error) {
    setProgress(String(data.error));
    throw new Error(data.error);
  }
  selected = data.data as ProductRecord;
  document.querySelectorAll(".cs-card").forEach((c) => c.classList.toggle("active", (c as HTMLElement).dataset.id === id));
  syncCategoryFromPage();
  const categoryInput = document.getElementById("cs-category") as HTMLInputElement | null;
  if (categoryInput && !categoryInput.value) {
    categoryInput.value = suggestedCategoryLabel(selected);
  }
  if (!pickedFile) {
    const img = document.getElementById("cs-image") as HTMLImageElement | null;
    const imageUrl = selected.images?.find((item) => item.primary)?.url || selected.images?.[0]?.url || "";
    if (img && imageUrl) {
      img.src = imageUrl;
      img.style.display = "block";
    }
  }
}

async function runAutofill(mode: string) {
  syncStoreFromPage();
  syncSizesFromPage();
  const listing = currentListing();
  if (!listing.title && !selected) {
    setProgress("Generate a listing first, or select a saved product.");
    return;
  }
  if (isMeeshoBulkTemplateStep() || isMeeshoBulkCatalogPage()) {
    setProgress("This is Meesho's Bulk Catalog Upload (Excel template). Catalog Studio will not click or change this page. Open Add Single Catalog to fill the product form.");
    return;
  }
  resetAutofill();
  startFillSession();
  const fillBtn = document.getElementById("cs-fill") as HTMLButtonElement | null;
  if (fillBtn) fillBtn.textContent = t("ext.filling");
  try {
  const marketplace = detectMarketplace();
  if (mode === "category") {
    setProgress("Category stays under your control on Meesho. Catalog Studio only reads the path you already selected.");
    if (fillBtn) fillBtn.textContent = fillButtonLabel();
    return;
  }
  let steps = planMeeshoFill(listing);
  if (mode === "basic") steps = steps.filter((s) => ["Product Name", "Name", "Item name", "Title", "Description"].includes(s.hint.labelText || ""));
  if (mode === "attributes") steps = steps.filter((s) => /color|pattern|fabric|material|generic|combo|print|ornament|quantity|size|length|origin|stitch|sleeve|brand|neck|fit|occasion|pack|category|tax|gst|hsn|wash|type|unit|weight/i.test(s.hint.labelText || ""));
  const results = await fillByHints(steps, (message, done, total) => {
    setProgress(message);
    if (typeof done === "number" && typeof total === "number") setMeter(done, total);
  });
  if (marketplace === "MEESHO") {
    const sizeResults = await fillSizeChoices(listing, setProgress);
    const chartResults = await fillSizeChart(listing, setProgress);
    results.push(...sizeResults, ...chartResults);
  }
  const filled = results.filter((item) => item.ok).length;
  setMeter(filled, results.length);
  setProgress(`Filled ${filled} of ${results.length} fields. Review, then submit yourself.`);
  if (fillBtn) fillBtn.textContent = fillButtonLabel();
  await sendRuntimeMessage({
    type: "API",
    path: "/extension/activity",
    init: {
      method: "POST",
      body: JSON.stringify({ marketplace, action: "AUTOFILL", productId: selected?.id || generated?.productId, details: { mode } }),
    },
  });
  } finally {
    stopFillSession();
    if (fillBtn) fillBtn.textContent = fillButtonLabel();
  }
}

function currentListing(): MappedListing {
  const notes = (document.getElementById("cs-notes") as HTMLTextAreaElement | null)?.value || "";
  const title = generated?.titles[generated.selectedTitle] || String(selected?.name || "");
  const description = generated?.descriptions[generated.selectedDesc] || String(selected?.description || "");
  const combo = parseCombo(notes);
  const split = splitPattern(generated?.pattern || String(selected?.pattern || ""), notes);
  const pageCategory = readMeeshoCategoryFromPage() || generated?.genericName || "";
  const defaults = defaultsForCategory(`${pageCategory} ${title}`, notes);
  const color = generated?.primaryColor || String(selected?.primaryColor || "");
  const productType = generated?.productType || String(selected?.productType || pageCategory);
  const storeName = detectedStore?.name || businessProfile?.name || "";
  const address = inputValue("cs-address") || businessProfile?.address || "";
  const pincode = inputValue("cs-pincode") || extractPincode(address);
  const styleCode = buildStyleCode(color, productType, inputValue("cs-style"));
  if (styleCode && !inputValue("cs-style")) setInput("cs-style", styleCode);
  if (!inputValue("cs-mrp")) setInput("cs-mrp", defaults.mrp);
  if (!inputValue("cs-price")) setInput("cs-price", defaults.sellingPrice);
  if (!inputValue("cs-inventory")) setInput("cs-inventory", String(sellerSettings.priceRule?.inventory || defaults.inventory));
  applyPriceRuleToInputs();
  const pack = sellerSettings.packaging || {};
  const ornamentation = generated?.ornamentation || detectOrnamentation(notes, description);
  const stitchType = defaults.stitchType;
  const pattern = ornamentation === "Embroidered" && stitchType === "Stitched"
    ? "Embroidered"
    : generated?.pattern || split.pattern;
  const sleeveSource = `${generated?.sleeveType || selected?.sleeveType || ""} ${title} ${description}`;
  const sleeveLength = mapSleeveLength(sleeveSource) || defaults.sleeveLength;
  const neckType = mapNeck(generated?.neckType || String(selected?.neckType || ""))
    || (/kurti|kurta|dress|gown|t-?shirt|tee|top|tunic/.test(`${pageCategory} ${title}`.toLowerCase()) ? "Round Neck" : "");
  return {
    title,
    gender: generated?.gender || String(selected?.gender || ""),
    color,
    pattern,
    printType: generated?.printType || split.printType,
    material: deriveFabric(generated?.material || String(selected?.material || ""), title, notes, description, defaults.fabric),
    description,
    hsn: inputValue("cs-hsn") || defaults.hsn,
    gst: inputValue("cs-gst") || defaults.gst,
    netWeight: inputValue("cs-weight") || defaults.netWeight,
    styleCode,
    genericName: deriveGenericName(title, pageCategory, generated?.genericName, generated?.productType || String(selected?.productType || "")),
    comboOf: generated?.comboOf || combo.label || defaults.comboOf,
    netQuantity: generated?.netQuantity || combo.quantity || defaults.netQuantity,
    ornamentation,
    sleeveType: generated?.sleeveType || String(selected?.sleeveType || ""),
    sleeveLength,
    neckType,
    fit: generated?.fit || String(selected?.fit || "") || defaults.fit,
    occasion: deriveOccasion(generated?.occasion || String(selected?.occasion || ""), notes, description, title) || defaults.occasion,
    size: [...selectedSizes][0] || defaults.size,
    selectedSizes: [...selectedSizes],
    fabricLength: defaults.fabricLength,
    countryOfOrigin: defaults.countryOfOrigin,
    manufacturerName: storeName,
    manufacturerAddress: address,
    manufacturerPincode: pincode,
    packerName: storeName,
    packerAddress: address,
    packerPincode: pincode,
    skuId: styleCode,
    lengthSize: defaults.fabricLength.replace(/ meters/i, ""),
    mrp: inputValue("cs-mrp") || defaults.mrp,
    sellingPrice: inputValue("cs-price") || defaults.sellingPrice,
    brand: deriveBrand(title, storeName),
    stitchType,
    garmentLength: mapGarmentLength(`${title} ${pageCategory}`, defaults.garmentLength) || defaults.garmentLength,
    mainCategory: pageCategory || deriveMainCategory(title, notes),
    packOf: defaults.packOf,
    waistRise: /pant|trouser|jean/.test(`${title} ${notes}`.toLowerCase()) ? "Mid Rise" : "",
    closure: /pant|trouser|jean/.test(`${title} ${notes}`.toLowerCase()) ? "Elasticated" : "",
    packageWeight: String(pack.weight || defaults.packageWeight),
    packageLength: String(pack.length || defaults.packageLength),
    packageWidth: String(pack.width || defaults.packageWidth),
    packageHeight: String(pack.height || defaults.packageHeight),
    packagingType: String(pack.type || defaults.packagingType),
    packagingUnit: defaults.packagingUnit,
    inventory: inputValue("cs-inventory") || defaults.inventory,
    washCare: defaults.washCare,
    garmentType: defaults.garmentType,
    sleeveStyling: mapSleeveStyling(sleeveSource, defaults.sleeveStyling),
    surfaceStyling: ornamentation === "Not Applicable" ? defaults.surfaceStyling : ornamentation,
  };
}

function inputValue(id: string) {
  return ((document.getElementById(id) as HTMLInputElement | null)?.value || "").trim();
}

function setInput(id: string, value: string) {
  const el = document.getElementById(id) as HTMLInputElement | null;
  if (el && value && !el.value) el.value = value;
}

function logoImg(size: number) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="${size}" height="${size}"><rect width="64" height="64" rx="16" fill="#0F766E"/><rect x="20" y="13" width="26" height="34" rx="5" fill="#5EEAD4" opacity="0.38"/><rect x="17" y="16" width="26" height="34" rx="5" fill="#99F6E4" opacity="0.7"/><rect x="13" y="20" width="28" height="30" rx="6" fill="#FFFFFF"/><rect x="18" y="26" width="11" height="3.2" rx="1.6" fill="#0F766E"/><rect x="18" y="32.5" width="18" height="2.4" rx="1.2" fill="#5EEAD4"/><rect x="18" y="38" width="14" height="2.4" rx="1.2" fill="#99F6E4"/><path d="M49 14.5l1.85 4.05L55 20.4l-4.15 1.85L49 26.3l-1.85-4.05L43 20.4l4.15-1.85L49 14.5z" fill="#FDE68A"/></svg>`;
  return `<img src="data:image/svg+xml;utf8,${encodeURIComponent(svg)}" alt="Catalog Studio" width="${size}" height="${size}" />`;
}

async function persistListingDefaults() {
  await storageSet({
    listingDefaults: {
      gst: inputValue("cs-gst"),
      hsn: inputValue("cs-hsn"),
      netWeight: inputValue("cs-weight"),
      styleCode: inputValue("cs-style"),
      packerPincode: inputValue("cs-pincode"),
      address: inputValue("cs-address"),
      mrp: inputValue("cs-mrp"),
      sellingPrice: inputValue("cs-price"),
      inventory: inputValue("cs-inventory"),
    },
  });
}

async function restoreListingDefaults() {
  const stored = await storageGet(["listingDefaults", "meeshoStore", "business"]);
  const defaults = stored.listingDefaults || {};
  if (sanitizeStoreName(stored.meeshoStore?.name || "")) {
    detectedStore = { ...stored.meeshoStore, name: sanitizeStoreName(stored.meeshoStore.name) };
  }
  if (stored.business) businessProfile = stored.business;
  setInput("cs-gst", defaults.gst || "");
  setInput("cs-hsn", defaults.hsn || "");
  setInput("cs-weight", defaults.netWeight || "");
  setInput("cs-style", defaults.styleCode || "");
  setInput("cs-pincode", defaults.packerPincode || "");
  setInput("cs-address", defaults.address || businessProfile?.address || "");
  setInput("cs-mrp", defaults.mrp || "");
  setInput("cs-price", defaults.sellingPrice || "");
  setInput("cs-inventory", defaults.inventory || "");
  syncStoreFromPage();
}

function parseCombo(notes: string) {
  const text = notes.toLowerCase();
  if (/set of\s*(\d+)/.test(text)) {
    const count = text.match(/set of\s*(\d+)/)?.[1] || "2";
    return { label: `Set of ${count}`, quantity: count };
  }
  if (/combo|pack of/.test(text)) {
    return { label: "Combo", quantity: "2" };
  }
  return { label: "Single", quantity: "1" };
}

function splitPattern(pattern: string | undefined, notes: string) {
  const blob = `${pattern || ""} ${notes}`.toLowerCase();
  const printTypes = ["floral", "geometric", "abstract", "animal", "striped", "checked", "solid"];
  const foundPrint = printTypes.find((item) => blob.includes(item));
  const printed = /print/.test(blob);
  return {
    pattern: printed ? "Printed" : foundPrint === "solid" ? "Solid" : pattern || "",
    printType: foundPrint ? foundPrint[0].toUpperCase() + foundPrint.slice(1) : pattern || "",
  };
}

function uniqueTexts(values: unknown[]) {
  return [...new Set(values.map((value) => String(value || "").trim()).filter(Boolean))];
}

function setProgress(message: string, isError = false) {
  const el = document.getElementById("cs-progress");
  if (!el) return;
  el.textContent = message;
  el.classList.toggle("error", isError);
}

function setMeter(done: number, total: number) {
  const doneEl = document.getElementById("cs-meter-done");
  const leftEl = document.getElementById("cs-meter-left");
  const bar = document.getElementById("cs-meter-bar") as HTMLElement | null;
  if (doneEl) doneEl.textContent = t("ext.done", { n: total ? done : 0 });
  if (leftEl) leftEl.textContent = total ? t("ext.left", { n: Math.max(total - done, 0) }) : "-";
  if (bar) bar.style.width = total ? `${Math.round((done / total) * 100)}%` : "0%";
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char] || char);
}

async function loadExtensionFeatures() {
  const ping = await sendRuntimeMessage<{ data?: Record<string, any>; error?: string }>({ type: "API", path: "/extension/ping" });
  if (!ping || ping.error) return;
  const data = ping.data || {};
  quota = data.quota || {};
  sellerSettings = data.settings || {};
  paintQuota();
  paintSettingsForm();
  const cats = await sendRuntimeMessage<{ data?: Array<{ id: string; name: string; path: string }> }>({ type: "API", path: "/extension/categories" });
  categoryOptions = cats?.data || [];
  void verifyShopLock();
}

function paintQuota() {
  const el = document.getElementById("cs-quota");
  if (!el) return;
  if (!quota.limit) {
    el.textContent = "";
    return;
  }
  el.textContent = t("ext.quotaLine", {
    plan: quota.plan || "FREE",
    used: quota.used ?? 0,
    limit: quota.limit,
  });
}

function paintSettingsForm() {
  const rule = sellerSettings.priceRule || {};
  const pack = sellerSettings.packaging || {};
  setInput("cs-retcut", String(rule.retCut ?? "1"));
  setInput("cs-mrpmul", String(rule.mrpMul ?? "1.9"));
  setInput("cs-set-inv", String(rule.inventory ?? "200"));
  setInput("cs-pk-l", String(pack.length || ""));
  setInput("cs-pk-w", String(pack.width || ""));
  setInput("cs-pk-h", String(pack.height || ""));
  setInput("cs-pk-wt", String(pack.weight || ""));
  setInput("cs-keywords", Array.isArray(sellerSettings.keywords) ? sellerSettings.keywords.join(", ") : "");
}

async function saveSellerSettings() {
  const body = {
    priceRule: {
      retCut: Number(inputValue("cs-retcut") || 1),
      mrpMul: Number(inputValue("cs-mrpmul") || 1.9),
      inventory: Number(inputValue("cs-set-inv") || 200),
    },
    packaging: {
      type: "Box",
      length: inputValue("cs-pk-l") || "28",
      width: inputValue("cs-pk-w") || "22",
      height: inputValue("cs-pk-h") || "6",
      weight: inputValue("cs-pk-wt") || "250",
    },
    keywords: inputValue("cs-keywords").split(",").map((item) => item.trim()).filter(Boolean),
  };
  const data = await sendRuntimeMessage<{ data?: Record<string, any>; error?: string }>({
    type: "API",
    path: "/extension/settings",
    init: { method: "PUT", body: JSON.stringify(body) },
  });
  if (data?.error) {
    setProgress(data.error, true);
    return;
  }
  sellerSettings = { ...sellerSettings, ...(data?.data || body) };
  setProgress(t("ext.saveSettings"));
}

function applyPriceRuleToInputs() {
  const price = Number(inputValue("cs-price"));
  if (!price) return;
  const rule = sellerSettings.priceRule || {};
  const mul = Number(rule.mrpMul || 1.9);
  if (!inputValue("cs-mrp") || Number(inputValue("cs-mrp")) === price) {
    setInput("cs-mrp", String(Math.round(price * mul)));
  }
}

async function verifyShopLock() {
  const name = sanitizeStoreName(detectedStore?.name || "");
  if (detectedStore && !name) detectedStore = null;
  else if (detectedStore && name) detectedStore = { ...detectedStore, name };
  let data = await postVerifyShop(name);
  if (isChromeLockMismatch(data)) {
    await sendRuntimeMessage({
      type: "API",
      path: "/extension/settings",
      init: { method: "PUT", body: JSON.stringify({ lockedShopName: name, lockedShopUid: meeshoUid() }) },
    });
    data = await postVerifyShop(name);
  }
  const el = document.getElementById("cs-shop-lock");
  if (!data?.data) return;
  shopLock = (data.data.status as typeof shopLock) || "wait";
  shopLockMessage = data.data.error || "";
  if (shopLock === "bad" && isChromeLockMismatch(data)) {
    shopLock = name ? "ok" : "wait";
    shopLockMessage = "";
  }
  if (!el) return;
  if (shopLock === "ok") {
    el.className = "cs-ok";
    el.textContent = t("ext.shopLockOk", { name: data.data.registered || name ? ` — ${data.data.registered || name}` : "" });
  } else if (shopLock === "bad") {
    el.className = "cs-note";
    el.textContent = shopLockMessage || t("ext.shopMismatch");
  } else {
    el.className = "cs-note";
    el.textContent = t("ext.needPair");
  }
}

function isChromeLockMismatch(data?: { data?: { status?: string; error?: string; registered?: string } }) {
  if (data?.data?.status !== "bad") return false;
  return isMeeshoPageChrome(data.data.registered || "") || isMeeshoPageChrome(data.data.error || "");
}

async function postVerifyShop(name: string) {
  return sendRuntimeMessage<{ data?: { status?: string; error?: string; registered?: string } }>({
    type: "API",
    path: "/extension/verify-shop",
    init: { method: "POST", body: JSON.stringify({ name, uid: meeshoUid() }) },
  });
}

function renderCategoryResults(query: string) {
  const box = document.getElementById("cs-cat-results");
  if (!box) return;
  const q = query.trim().toLowerCase();
  if (!q) {
    box.innerHTML = "";
    return;
  }
  const hits = categoryOptions.filter((item) => `${item.name} ${item.path}`.toLowerCase().includes(q)).slice(0, 6);
  box.innerHTML = hits
    .map((item) => `<button type="button" class="ghost" data-cat="${escapeHtml(item.name)}">${escapeHtml(item.path || item.name)}</button>`)
    .join("");
  box.querySelectorAll<HTMLButtonElement>("[data-cat]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const input = document.getElementById("cs-category") as HTMLInputElement | null;
      if (input) input.value = btn.dataset.cat || "";
      applyCategoryDefaults(btn.dataset.cat || "");
      box.innerHTML = "";
    });
  });
}

async function applyFillGaps(category: string, notes: string) {
  if (!generated) return;
  const data = await sendRuntimeMessage<{ data?: { fields?: Record<string, string> } }>({
    type: "API",
    path: "/extension/fill-gaps",
    init: {
      method: "POST",
      body: JSON.stringify({
        category,
        notes,
        current: generated,
        missing: ["material", "gst", "hsn", "comboOf", "netQuantity", "occasion", "fit", "genericName"],
      }),
    },
  });
  const fields = data?.data?.fields || {};
  generated = { ...generated, ...fields };
}

whenReady(start);
