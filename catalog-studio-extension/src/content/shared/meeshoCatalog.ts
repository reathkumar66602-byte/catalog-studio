import { visible } from "./fieldFinder";

export type ProductLike = {
  name?: string;
  productType?: string;
  category?: string;
  subcategory?: string;
  gender?: string;
};

const STEP_NOISE = /select category|add product details|add images|image guidelines|discard catalog|save and go back|product 1|front view/i;

export function pagePath(url = location.href) {
  try {
    return new URL(url, location.origin).pathname.toLowerCase();
  } catch {
    return url.split("?")[0].toLowerCase();
  }
}

export function isMeeshoCatalogListPage(url = location.href) {
  const path = pagePath(url);
  return /\/cataloging\/[^/]+\/catalogs\/?$/.test(path);
}

export function isMeeshoSupplierLoginPage(url = location.href) {
  const path = pagePath(url);
  if (/\/login\b|\/signin\b|\/auth\b/.test(path)) {
    return true;
  }
  const text = `${typeof document === "undefined" ? "" : document.title} ${hostPageText(1800)}`.toLowerCase();
  return /login to your supplier panel|create your supplier account|email id or mobile number/.test(text);
}

export function isMeeshoAddCatalogFlow(url = location.href) {
  if (isMeeshoSupplierLoginPage(url)) {
    return false;
  }
  const haystack = `${url} ${typeof document === "undefined" ? "" : document.title}`.toLowerCase();
  if (/add.?single|add.?catalog|single.?catalog|bulk\/add|product.?details|select.?category|\/catalogs\/(add|new|create|upload|edit)/i.test(haystack)) {
    return true;
  }
  return hasAddCatalogHeading();
}

export function isMeeshoBulkCatalogPage(url = location.href) {
  const haystack = `${url} ${pagePath(url)} ${typeof document === "undefined" ? "" : document.title}`.toLowerCase();
  return /meesho\.com/i.test(haystack) && /\/catalogs\/bulk|bulk catalog|bulk\/add|bulk-upload/.test(haystack);
}

export function isMeeshoBulkTemplateStep() {
  const text = `${document.title} ${hostPageText(3500)}`;
  return /upload template file|generate prefilled template|download empty template/i.test(text);
}

/** Excel template step only. A bulk page that already shows the product form is filled the same way as single catalog. */
export function isMeeshoBulkExcelOnly() {
  return isMeeshoBulkTemplateStep() && !isMeeshoProductDetailsPage();
}

export function isMeeshoCatalogPage(url = location.href) {
  const haystack = `${url} ${pagePath(url)} ${typeof document === "undefined" ? "" : document.title}`.toLowerCase();
  if (!/meesho\.com/i.test(haystack)) {
    return false;
  }
  return /cataloging|\/catalogs|bulk\/add|bulk catalog|add-catalog|single catalog|add single|\/listing/.test(haystack);
}

export function shouldScanMeeshoForm(url = location.href) {
  if (isMeeshoAddCatalogFlow(url) || isMeeshoBulkCatalogPage(url)) return true;
  if (isMeeshoCatalogListPage(url) && !hasAddCatalogHeading()) return false;
  return isMeeshoProductDetailsPage() || isMeeshoCategoryPickerVisible();
}

export function isMeeshoProductDetailsPage() {
  if (isMeeshoCatalogListPage() && !hasAddCatalogHeading()) return false;
  if (isMeeshoCategoryPickerVisible()) return false;
  const text = hostPageText(8000);
  return /gst\s*%|hsn code|product name|net weight|manufacturer name/i.test(text);
}

export function isMeeshoCategoryPickerVisible() {
  if (isMeeshoCatalogListPage() && !hasAddCatalogHeading()) return false;
  return pathFromCategoryPicker().length >= 2;
}

function hasAddCatalogHeading() {
  const heading = `${document.title} ${document.querySelector("h1")?.textContent || ""} ${document.querySelector("h2")?.textContent || ""}`;
  return /add single|add catalog|select category|add product details/i.test(heading);
}

export function hostPageText(max = 6000) {
  if (!document.body) return "";
  const parts: string[] = [];
  let len = 0;
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      const el = node.parentElement;
      if (!el) return NodeFilter.FILTER_REJECT;
      if (el.closest("#cs-sidebar") || el.id === "cs-fab" || el.closest("#cs-fab")) return NodeFilter.FILTER_REJECT;
      const tag = el.tagName;
      if (tag === "SCRIPT" || tag === "STYLE" || tag === "NOSCRIPT") return NodeFilter.FILTER_REJECT;
      return NodeFilter.FILTER_ACCEPT;
    },
  });
  while (len < max) {
    const node = walker.nextNode();
    if (!node) break;
    const text = (node.textContent || "").replace(/\s+/g, " ").trim();
    if (!text) continue;
    parts.push(text);
    len += text.length;
  }
  return parts.join(" ");
}

export const CATEGORY_PATHS: Record<string, string[]> = {
  "Kurti Fabrics": ["Women Fashion", "Ethnic Wear", "Kurtis, Sets & Fabrics", "Kurti Fabrics"],
  Kurtis: ["Women Fashion", "Ethnic Wear", "Kurtis, Sets & Fabrics", "Kurtis"],
  Sarees: ["Women Fashion", "Ethnic Wear", "Sarees"],
  "Western Gowns": ["Women Fashion", "Western Wear", "Dresses, Gowns & Jumpsuits", "Western Gowns"],
  Dresses: ["Women Fashion", "Western Wear", "Dresses, Gowns & Jumpsuits", "Dresses"],
  "Tops & Tunics": ["Women Fashion", "Western Wear", "Tops, Tshirts & Shirts", "Tops & Tunics"],
  "T-shirts": ["Women Fashion", "Western Wear", "Tops, Tshirts & Shirts", "T-shirts"],
  Shirts: ["Women Fashion", "Western Wear", "Tops, Tshirts & Shirts", "Shirts"],
};

export function readMeeshoCategoryFromPage() {
  const path = readMeeshoCategoryPath();
  return path[path.length - 1] || "";
}

let cachedPath: { at: number; path: string[] } | null = null;

export function readMeeshoCategoryPath() {
  if (cachedPath && Date.now() - cachedPath.at < 500) return cachedPath.path;
  const picked = pathFromCategoryPicker();
  const path = picked.length ? picked : pathFromBreadcrumb();
  cachedPath = { at: Date.now(), path };
  return path;
}

export function pathFromCategoryPicker() {
  if (isMeeshoCatalogListPage() && !hasAddCatalogHeading()) return [];
  const marked = Array.from(document.querySelectorAll<HTMLElement>(
    "[aria-selected='true'], [aria-current='true'], [class*='selected' i], [class*='chosen' i]",
  )).filter((el) => isCategoryOption(el) || (el.children.length === 0 && sanitizeOption(el)));
  const parents = new Set<HTMLElement>();
  for (const el of marked) {
    if (el.parentElement && !el.closest("#cs-sidebar")) parents.add(el.parentElement);
  }
  if (!parents.size) {
    return pathFromCategoryColumns();
  }
  const columns = [...parents]
    .map((parent) => {
      const kids = Array.from(parent.children).filter((node): node is HTMLElement => node instanceof HTMLElement);
      return {
        x: parent.getBoundingClientRect().left,
        y: parent.getBoundingClientRect().top,
        selected: selectedLabel(kids),
        count: kids.length,
      };
    })
    .filter((col) => col.selected && col.count >= 3 && col.x >= 0)
    .sort((a, b) => a.x - b.x || a.y - b.y);
  const unique: string[] = [];
  for (const col of columns) {
    if (unique.includes(col.selected)) continue;
    unique.push(col.selected);
  }
  return unique;
}

function pathFromCategoryColumns() {
  const items = Array.from(document.querySelectorAll<HTMLElement>("li, [role='option'], [role='button'], button, a, p, span"))
    .filter(isCategoryOption);
  const byParent = new Map<HTMLElement, HTMLElement[]>();
  for (const el of items) {
    const parent = el.parentElement;
    if (!parent || parent.closest("#cs-sidebar")) continue;
    const list = byParent.get(parent) || [];
    list.push(el);
    byParent.set(parent, list);
  }

  const columns = [...byParent.entries()]
    .filter(([, kids]) => kids.length >= 3)
    .map(([parent, kids]) => ({
      x: parent.getBoundingClientRect().left,
      y: parent.getBoundingClientRect().top,
      selected: selectedLabel(kids),
      count: kids.length,
    }))
    .filter((col) => col.selected && col.x >= 0)
    .sort((a, b) => a.x - b.x || a.y - b.y);

  const unique: string[] = [];
  for (const col of columns) {
    if (unique.includes(col.selected)) continue;
    unique.push(col.selected);
  }
  return unique;
}

function sanitizeOption(el: HTMLElement) {
  const text = optionText(el);
  return Boolean(text && text.length >= 2 && text.length <= 48 && !STEP_NOISE.test(text) && !/^\d+$/.test(text));
}

function isCategoryOption(el: HTMLElement) {
  if (el.closest("#cs-sidebar") || el.id === "cs-fab") return false;
  if (!visible(el)) return false;
  const text = optionText(el);
  if (!text || text.length < 2 || text.length > 48) return false;
  if (STEP_NOISE.test(text)) return false;
  if (/^\d+$/.test(text)) return false;
  return true;
}

function optionText(el: HTMLElement) {
  const own = Array.from(el.childNodes)
    .filter((node) => node.nodeType === Node.TEXT_NODE)
    .map((node) => (node.textContent || "").replace(/\s+/g, " ").trim())
    .find((text) => text.length >= 2);
  if (own) return own;
  if (el.children.length === 1 && el.children[0].children.length === 0) {
    return (el.children[0].textContent || "").replace(/\s+/g, " ").trim();
  }
  if (el.children.length === 0) {
    return (el.textContent || "").replace(/\s+/g, " ").trim();
  }
  return "";
}

function selectedLabel(kids: HTMLElement[]) {
  const marked = kids.find((el) => {
    const host = `${el.className} ${el.parentElement?.className || ""}`;
    return el.getAttribute("aria-selected") === "true"
      || el.getAttribute("aria-current") === "true"
      || /\b(selected|active|chosen|highlight|current)\b/i.test(host);
  });
  if (marked) return optionText(marked);
  const scored = kids
    .map((el) => ({ el, text: optionText(el), score: selectionScore(el) }))
    .filter((item) => item.text && item.score >= 2);
  scored.sort((a, b) => b.score - a.score);
  return scored[0]?.text || "";
}

function selectionScore(el: HTMLElement) {
  let score = 0;
  const host = `${el.className} ${el.parentElement?.className || ""} ${el.getAttribute("aria-selected") || ""}`;
  if (el.getAttribute("aria-selected") === "true" || el.getAttribute("aria-current") === "true") score += 8;
  if (/\b(selected|active|chosen|highlight|current)\b/i.test(host)) score += 6;
  try {
    const style = window.getComputedStyle(el);
    const parentStyle = el.parentElement ? window.getComputedStyle(el.parentElement) : null;
    if (isMeeshoHighlight(style.backgroundColor) || isMeeshoHighlight(parentStyle?.backgroundColor || "")) score += 5;
    if (isMeeshoHighlight(style.color) || isMeeshoHighlight(style.borderBottomColor)) score += 3;
    const weight = style.fontWeight;
    if (Number(weight) >= 600 || weight === "bold") score += 1;
  } catch {
    // Meesho may detach nodes while we scan
  }
  return score;
}

function isMeeshoHighlight(cssColor: string) {
  const match = cssColor.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
  if (!match) return false;
  const red = Number(match[1]);
  const green = Number(match[2]);
  const blue = Number(match[3]);
  if (red > 240 && green > 240 && blue > 240) return false;
  if (red + green + blue < 40) return false;
  return (red > 90 && blue > 90 && red - green > 15) || (red > 150 && green < 120);
}

function pathFromBreadcrumb() {
  const nodes = Array.from(document.querySelectorAll<HTMLElement>("div, span, p, nav, h1, h2, h3"));
  for (const el of nodes) {
    if (el.closest("#cs-sidebar")) continue;
    const text = (el.innerText || "").replace(/\s+/g, " ").trim();
    if (!text.includes("/") || text.length > 120) continue;
    const parts = text.split("/").map((part) => part.trim()).filter(Boolean);
    if (parts.length >= 3 && parts.length <= 6 && parts.every((part) => part.length < 48 && !STEP_NOISE.test(part))) {
      return parts;
    }
  }
  return [];
}

function isWomenAudience(blob: string) {
  return /\bwom[ae]n|ladies|female|girls?\b/.test(blob);
}

function isMenAudience(blob: string) {
  return (/\bmen\b|\bmale\b|\bboys?\b/.test(blob) || /\bmens\b/.test(blob)) && !isWomenAudience(blob);
}

export function categoryClickPath(product: ProductLike): string[] {
  const pagePath = readMeeshoCategoryPath();
  if (pagePath.length >= 2) return pagePath;

  const blob = [product.gender, product.category, product.subcategory, product.productType, product.name]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();

  if (/\bsaree/.test(blob)) {
    return CATEGORY_PATHS.Sarees;
  }
  if (/kurti fabric|\bunstitched\b/.test(blob)) {
    return CATEGORY_PATHS["Kurti Fabrics"];
  }
  if (/\btunic|\btops?\b/.test(blob)) {
    return CATEGORY_PATHS["Tops & Tunics"];
  }
  if (/\bkurti/.test(blob) || /\bkurtas?\b/.test(blob)) {
    if (/\bmen|male/.test(blob) && !/\bwom[ae]n|ladies|female/.test(blob)) {
      return ["Men Fashion", "Ethnic Wear", "Kurtas"];
    }
    return CATEGORY_PATHS.Kurtis;
  }
  if (/\bt-?shirt|\btee\b/.test(blob)) {
    if (isMenAudience(blob)) {
      return ["Men Fashion", "Western Wear", "T-shirts"];
    }
    return CATEGORY_PATHS["T-shirts"];
  }
  if (/\bshirts?\b/.test(blob)) {
    if (isMenAudience(blob)) {
      return ["Men Fashion", "Casual Wear", "Shirts"];
    }
    return CATEGORY_PATHS.Shirts;
  }
  if (/\bgown|jumpsuit/.test(blob)) {
    return CATEGORY_PATHS["Western Gowns"];
  }
  if (/\bdress|frock/.test(blob)) {
    return CATEGORY_PATHS.Dresses;
  }

  return [product.subcategory, product.productType, product.category].filter(
    (value): value is string => Boolean(value && String(value).trim()),
  );
}

export function suggestedCategoryLabel(product: ProductLike) {
  const fromPage = readMeeshoCategoryFromPage();
  if (fromPage) return fromPage;
  const path = categoryClickPath(product);
  return path[path.length - 1] || product.subcategory || product.productType || product.category || "";
}

export async function clickCategoryPath(labels: string[], onProgress?: (message: string) => void) {
  const results: { label: string; ok: boolean }[] = [];
  for (const label of labels) {
    onProgress?.(`Selecting ${label}...`);
    const ok = clickVisibleCategory(label);
    results.push({ label, ok });
    await delay(350);
  }
  return results;
}

export function clickVisibleCategory(label: string) {
  const needle = normalize(label);
  if (!needle) return false;
  const nodes = Array.from(document.querySelectorAll<HTMLElement>("div, span, li, button, a, p, [role='button']"));
  const matches = nodes
    .filter((el) => {
      if (el.closest("#cs-sidebar") || el.id === "cs-fab") return false;
      if (!visible(el)) return false;
      const text = normalize(optionText(el) || el.innerText || el.textContent || "");
      if (!text || text.length > 64) return false;
      return text === needle;
    })
    .sort((a, b) => textLen(a) - textLen(b));
  const target = matches[0];
  if (!target) return false;
  target.click();
  return true;
}

function textLen(el: HTMLElement) {
  return (el.innerText || el.textContent || "").trim().length;
}

function normalize(value: string) {
  return value.toLowerCase().replace(/\s+/g, " ").trim();
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
