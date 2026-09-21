import { findField } from "./fieldFinder";

export type MeeshoStore = {
  name: string;
  source: "header" | "storage" | "manufacturer-field" | "page-json" | "title";
};

const NAV_NOISE = [
  "home",
  "catalog",
  "catalogs",
  "orders",
  "payments",
  "returns",
  "inventory",
  "account",
  "help",
  "settings",
  "notifications",
  "search",
  "add catalog",
  "add single catalog",
  "add product details",
  "select category",
  "meesho",
  "supplier",
  "dashboard",
  "logout",
  "profile",
  "discard catalog",
  "save and go back",
  "image guidelines",
  "front view",
  "product 1",
  "connected",
  "ai ready",
  "generate",
  "catalog studio",
  "manageorder",
  "supplier panel",
  "seller panel",
  "login to meesho supplier panel",
  "login to your supplier panel",
  "login to supplier panel",
  "select",
  "selected",
  "choose",
  "optional",
  "required",
];

const COLOR_NOISE = new Set([
  "purple", "navy", "navy blue", "blue", "red", "pink", "black", "white", "green", "yellow",
  "orange", "grey", "gray", "brown", "beige", "cream", "maroon", "olive", "teal", "gold",
  "silver", "wine", "rust", "peach", "mustard", "khaki", "lavender", "coral", "magenta",
]);

const PREFERRED_NAME_KEYS = [
  "shopName", "shop_name", "storeName", "store_name", "supplierName", "supplier_name",
  "sellerName", "seller_name", "businessName", "business_name", "firmName", "companyName",
  "displayName", "legalName",
];

export function detectMeeshoStore(): MeeshoStore | null {
  const found = [
    storeFromShopProfile(),
    storeFromManufacturerField(),
    storeFromStorage(),
    storeFromPageJson(),
    storeFromHeader(),
    storeFromTitle(),
  ].filter((item): item is MeeshoStore => Boolean(item?.name));
  if (!found.length) return null;
  return found.sort((a, b) => scoreStoreName(b.name, b.source) - scoreStoreName(a.name, a.source))[0];
}

export function readManufacturerNameFromForm() {
  for (const label of ["Manufacturer Name", "Manufacturer", "Packer Name", "Shop Name"]) {
    const match = findField({ labelText: label });
    if (!match) continue;
    const value = fieldText(match.element);
    if (sanitizeStoreName(value)) return sanitizeStoreName(value);
  }
  return "";
}

export function meeshoUid() {
  try {
    const href = location.href;
    const fromQuery = href.match(/[?&](?:supplierId|supplier_id|shopId|shop_id)=([^&]+)/i);
    if (fromQuery?.[1]) return decodeURIComponent(fromQuery[1]).slice(0, 80);
    const fromPath = href.match(/\/cataloging\/([^/?#]+)/i);
    const slug = fromPath?.[1] || "";
    if (slug && !/^(new|v3|panel|add|single)$/i.test(slug)) return slug.slice(0, 80);
  } catch {
    // ignore
  }
  return "";
}

function storeFromShopProfile(): MeeshoStore | null {
  const img = document.querySelector<HTMLImageElement>('img[alt="shop-profile"],img[alt*="shop-profile" i]');
  if (!img) return null;
  let box: HTMLElement | null = img.parentElement;
  for (let hop = 0; box && hop < 3; hop += 1) {
    const cand = Array.from(box.querySelectorAll("span,p,h1,h2,h3,div,a"));
    for (const el of cand) {
      if (el.children.length) continue;
      const name = sanitizeStoreName((el.textContent || "").trim());
      if (name) return { name, source: "header" };
    }
    box = box.parentElement;
  }
  return null;
}

function storeFromManufacturerField(): MeeshoStore | null {
  const name = sanitizeStoreName(readManufacturerNameFromForm());
  if (!name) return null;
  return { name, source: "manufacturer-field" };
}

function storeFromStorage(): MeeshoStore | null {
  const blobs: unknown[] = [];
  try {
    for (let i = 0; i < localStorage.length; i += 1) {
      const key = localStorage.key(i);
      if (!key) continue;
      blobs.push(localStorage.getItem(key));
    }
  } catch {
    // page may block storage
  }
  try {
    for (let i = 0; i < sessionStorage.length; i += 1) {
      const key = sessionStorage.key(i);
      if (!key) continue;
      blobs.push(sessionStorage.getItem(key));
    }
  } catch {
    // ignore
  }
  try {
    blobs.push(document.cookie);
  } catch {
    // ignore
  }
  const names = blobs.flatMap((blob) => collectStoreNames(blob));
  const name = pickBestName(names);
  return name ? { name, source: "storage" } : null;
}

function storeFromPageJson(): MeeshoStore | null {
  const names: string[] = [];
  const scripts = Array.from(document.querySelectorAll("script"));
  for (const script of scripts) {
    const id = (script.id || "").toLowerCase();
    const type = (script.type || "").toLowerCase();
    if (id.includes("next") || id.includes("data") || type.includes("json") || script.textContent?.trim().startsWith("{")) {
      names.push(...collectStoreNames(script.textContent));
    }
  }
  const w = window as unknown as Record<string, unknown>;
  for (const key of ["__NEXT_DATA__", "__INITIAL_STATE__", "__PRELOADED_STATE__", "supplier", "store"]) {
    names.push(...collectStoreNames(w[key]));
  }
  const name = pickBestName(names);
  return name ? { name, source: "page-json" } : null;
}

function storeFromHeader(): MeeshoStore | null {
  const scopes = Array.from(document.querySelectorAll<HTMLElement>(
    "header, nav, [class*='header' i], [class*='navbar' i], [class*='topbar' i], [class*='AppBar' i], [class*='user' i], [class*='profile' i], [class*='avatar' i], [class*='account' i], [class*='shop' i], [class*='seller' i], [class*='supplier' i]",
  ));
  const roots = scopes.length ? scopes : [document.body];
  const candidates: string[] = [];
  for (const root of roots) {
    const nodes = Array.from(root.querySelectorAll<HTMLElement>("span, div, p, a, strong, button, h1, h2, h3"));
    for (const el of nodes) {
      if (el.closest("#cs-sidebar") || el.id === "cs-fab") continue;
      const raw = el.innerText || el.textContent || el.getAttribute("title") || el.getAttribute("aria-label") || "";
      const name = sanitizeStoreName(raw);
      if (!name) continue;
      const rect = el.getBoundingClientRect();
      const looksLikeShop = /store|shop|mart|fashion|krishna/i.test(name);
      if (!looksLikeShop && (rect.top > 160 || rect.height > 80 || rect.width > 420 || rect.height < 1)) continue;
      candidates.push(name);
    }
  }
  const name = pickBestName(candidates);
  return name ? { name, source: "header" } : null;
}

function storeFromTitle(): MeeshoStore | null {
  const parts = document.title.split(/[|\-–]/).map((part) => sanitizeStoreName(part));
  const name = pickBestName(parts.filter((item): item is string => Boolean(item)));
  return name ? { name, source: "title" } : null;
}

function collectStoreNames(value: unknown, depth = 0): string[] {
  if (value == null || depth > 6) return [];
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      try {
        return collectStoreNames(JSON.parse(trimmed), depth + 1);
      } catch {
        return [];
      }
    }
    if (trimmed.includes("=") && trimmed.includes(";")) {
      const names: string[] = [];
      for (const part of trimmed.split(";")) {
        const [key, ...rest] = part.split("=");
        if (isIdentifierKey(key || "")) continue;
        if (/shop|store|supplier|seller|business/i.test(key || "")) {
          const found = sanitizeStoreName(safeDecode(rest.join("=") || ""));
          if (found) names.push(found);
        }
      }
      return names;
    }
    return [];
  }
  if (Array.isArray(value)) {
    return value.flatMap((item) => collectStoreNames(item, depth + 1));
  }
  if (typeof value === "object") {
    const record = value as Record<string, unknown>;
    const names: string[] = [];
    for (const key of PREFERRED_NAME_KEYS) {
      const found = sanitizeStoreName(String(record[key] || ""));
      if (found) names.push(found);
    }
    for (const [key, nested] of Object.entries(record)) {
      if (isIdentifierKey(key)) continue;
      if (/shop|store|supplier|seller|business|firm/i.test(key) && typeof nested === "string") {
        const found = sanitizeStoreName(nested);
        if (found) names.push(found);
      } else if (typeof nested === "object") {
        names.push(...collectStoreNames(nested, depth + 1));
      }
    }
    return names;
  }
  return [];
}

function pickBestName(names: string[]) {
  const unique = [...new Set(names.filter(Boolean))];
  return unique.sort((a, b) => scoreStoreName(b) - scoreStoreName(a))[0] || "";
}

function isIdentifierKey(key: string) {
  const normalized = key.replace(/[- ]/g, "_");
  return /(^|_)(id|identifier|identifier_?name|code|slug|uuid|token|key)s?$/i.test(normalized)
    || /Id$/.test(key);
}

function looksLikeIdentifier(name: string) {
  return /^[a-z0-9]{4,10}$/i.test(name)
    && /\d/.test(name)
    && /[a-z]/i.test(name)
    && !/store|shop|mart/i.test(name);
}

export function isMeeshoPageChrome(raw: string) {
  return /login to|sign in|(supplier|seller) panel/i.test(raw || "");
}

export function sanitizeStoreName(raw: string) {
  const first = (raw || "").split("\n")[0] || "";
  const name = first
    .replace(/[▼▾▿⏷›»•·]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  if (!name || name.length < 3 || name.length > 60) return "";
  if (NAV_NOISE.includes(name.toLowerCase())) return "";
  if (isMeeshoPageChrome(name)) return "";
  if (/https?:|www\./i.test(name)) return "";
  if (/^\d+$/.test(name)) return "";
  if (/\b\d{6}\b/.test(name)) return "";
  if (/[^a-z0-9 .'_&()/-]/i.test(name)) return "";
  if (/\b(select|add|save|discard|catalog|product|image)\b/i.test(name) && name.includes(" ")) return "";
  if (looksLikeIdentifier(name)) return "";
  if (looksLikeListingToken(name)) return "";
  return name;
}

function looksLikeListingToken(name: string) {
  const lower = name.toLowerCase();
  if (COLOR_NOISE.has(lower)) return true;
  if (/^(navy|sky|light|dark|off)\s+(blue|green|pink|grey|gray|brown|white)$/i.test(name)) return true;
  if (/^[a-z]+-[a-z0-9]+-d?\d{2,}$/i.test(name.replace(/\s+/g, ""))) return true;
  return false;
}

function scoreStoreName(name: string, source?: MeeshoStore["source"]) {
  let score = name.length;
  if (/[A-Z]/.test(name) && /[a-z]/.test(name)) score += 8;
  if (/store|shop|mart|fashion|style|krishna/i.test(name)) score += 20;
  if (name.includes(" ")) score += 10;
  if (source === "manufacturer-field") score += 6;
  if (source === "header") score += 4;
  return score;
}

function fieldText(el: HTMLElement) {
  if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement) {
    return el.value.trim();
  }
  return (el.textContent || "").trim();
}

function safeDecode(value: string) {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}
