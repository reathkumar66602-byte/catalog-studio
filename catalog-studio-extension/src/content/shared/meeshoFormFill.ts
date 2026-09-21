import { findField, visible, type SelectorHint } from "./fieldFinder";
import { isMeasureLabel, listingFieldValues, matchFieldValue, normalizeKey } from "./listingValues";
import type { FillStep, MappedListing } from "./marketplaces";
import { meeshoSteps } from "./marketplaces";
import { closeOpenMenus, isDropdownElement, selectCustomDropdown, selectNativeDropdown, setCheckbox, triggerReactInputEvents, verifyFieldValue, type FillResult } from "./autofillEngine";

export type DiscoveredField = {
  label: string;
  element: HTMLElement;
  type: FillStep["type"];
};

export type SizeMeasures = { length: string; bust: string; waist: string; hip: string; shoulder: string };

const SKIP_LABELS = /required|optional|guidelines|characters|discard|save|submit|product 1|front view|add product|add images|select category/i;

const ALPHA_SIZES = ["XS", "S", "M", "L", "XL", "XXL", "2XL", "3XL", "4XL", "5XL", "6XL", "7XL", "8XL"];
const NUMERIC_SIZES = ["28", "30", "32", "34", "36", "38", "40", "42", "44", "46"];
const EXTRA_SIZES = ["Free Size", "Semi Stitched"];
const SIZE_TOKEN = new Set([...ALPHA_SIZES, ...NUMERIC_SIZES, ...EXTRA_SIZES]);
const ALPHA_FROM_NUMBER: Record<string, string> = { "36": "S", "38": "M", "40": "L", "42": "XL", "44": "XXL" };

const KURTI_CHART: Record<string, SizeMeasures> = {
  XS: { length: "40", bust: "32", waist: "28", hip: "36", shoulder: "13.5" },
  S: { length: "41", bust: "34", waist: "30", hip: "38", shoulder: "14" },
  M: { length: "42", bust: "36", waist: "32", hip: "40", shoulder: "14.5" },
  L: { length: "42", bust: "38", waist: "34", hip: "42", shoulder: "15" },
  XL: { length: "43", bust: "40", waist: "36", hip: "44", shoulder: "15.5" },
  XXL: { length: "43", bust: "42", waist: "38", hip: "46", shoulder: "16" },
  "2XL": { length: "43", bust: "42", waist: "38", hip: "46", shoulder: "16" },
  "3XL": { length: "44", bust: "44", waist: "40", hip: "48", shoulder: "16.5" },
  "4XL": { length: "44", bust: "46", waist: "42", hip: "50", shoulder: "17" },
  "5XL": { length: "45", bust: "48", waist: "44", hip: "52", shoulder: "17.5" },
  "6XL": { length: "45", bust: "50", waist: "46", hip: "54", shoulder: "18" },
};

const TOP_CHART: Record<string, SizeMeasures> = {
  XS: { length: "23", bust: "32", waist: "26", hip: "34", shoulder: "13" },
  S: { length: "24", bust: "34", waist: "28", hip: "36", shoulder: "13.5" },
  M: { length: "25", bust: "36", waist: "30", hip: "38", shoulder: "14" },
  L: { length: "26", bust: "38", waist: "32", hip: "40", shoulder: "14.5" },
  XL: { length: "27", bust: "40", waist: "34", hip: "42", shoulder: "15" },
  XXL: { length: "28", bust: "42", waist: "36", hip: "44", shoulder: "15.5" },
  "2XL": { length: "28", bust: "42", waist: "36", hip: "44", shoulder: "15.5" },
};

const TSHIRT_CHART: Record<string, SizeMeasures> = {
  XS: { length: "25", bust: "34", waist: "32", hip: "36", shoulder: "15.5" },
  S: { length: "26", bust: "36", waist: "34", hip: "38", shoulder: "16" },
  M: { length: "27", bust: "38", waist: "36", hip: "40", shoulder: "17" },
  L: { length: "28", bust: "40", waist: "38", hip: "42", shoulder: "18" },
  XL: { length: "29", bust: "42", waist: "40", hip: "44", shoulder: "19" },
  XXL: { length: "30", bust: "44", waist: "42", hip: "46", shoulder: "20" },
  "2XL": { length: "30", bust: "44", waist: "42", hip: "46", shoulder: "20" },
};

const PANT_SIZES = ["28", "30", "32", "34", "36", "38"];
const FALLBACK_APPAREL = ["S", "M", "L", "XL", "XXL"];

export function discoverPageFields(): DiscoveredField[] {
  const found: DiscoveredField[] = [];
  const seen = new Set<HTMLElement>();
  const nodes = Array.from(document.querySelectorAll<HTMLElement>("label, p, span, dt, h3, h4, h5, div"));
  for (const node of nodes) {
    if (node.closest("#cs-sidebar") || node.id === "cs-fab" || node.closest("table")) continue;
    const label = ownLabel(node);
    if (!label || label.length < 2 || label.length > 56 || SKIP_LABELS.test(label) || isMeasureLabel(label)) continue;
    const field = fieldNear(node);
    if (!field || seen.has(field) || field.closest("#cs-sidebar") || field.closest("table") || isSizeGridField(field)) continue;
    seen.add(field);
    found.push({ label, element: field, type: inferType(field, label) });
  }
  return found;
}

const FIELD_SELECTOR = "input, textarea, select, [role='combobox'], [aria-haspopup='listbox'], [contenteditable='true']";

function fieldNear(labelEl: HTMLElement) {
  let scope: HTMLElement | null = labelEl;
  for (let i = 0; i < 5 && scope; i++) {
    const dropdown = findDropdownIn(scope);
    if (dropdown) return dropdown;
    const combo = firstVisible(scope, "select, [role='combobox'], [aria-haspopup='listbox']");
    if (combo && !combo.closest("table")) return combo;
    const input = firstVisible(scope, "input, textarea, [contenteditable='true']");
    if (input && !input.closest("table")) return input;
    scope = scope.parentElement;
  }
  const next = labelEl.nextElementSibling as HTMLElement | null;
  if (next && isFieldLike(next) && visible(next)) return next;
  const nested = next?.querySelector?.<HTMLElement>(FIELD_SELECTOR);
  if (nested && visible(nested)) return nested;
  const fromSelectText = findSelectPlaceholder(labelEl.parentElement);
  if (fromSelectText) return fromSelectText;
  return findField({ labelText: (labelEl.innerText || "").split("\n")[0] })?.element || null;
}

function findDropdownIn(scope: HTMLElement) {
  const nodes = Array.from(scope.querySelectorAll<HTMLElement>(`${FIELD_SELECTOR}, [class*='select'], [class*='Select'], [class*='dropdown']`));
  return nodes.find((el) => visible(el) && !el.closest("#cs-sidebar") && !el.closest("table") && isFieldLike(el)) || null;
}

function findSelectPlaceholder(scope: HTMLElement | null) {
  if (!scope) return null;
  return Array.from(scope.querySelectorAll<HTMLElement>("div, span, button, input")).find((el) => {
    if (el.closest("#cs-sidebar") || el.closest("table") || !visible(el)) return false;
    const text = (el.innerText || (el as HTMLInputElement).placeholder || "").trim().split("\n")[0];
    return /^(select|choose|pick)$/i.test(text);
  }) || null;
}

function isFieldLike(el: HTMLElement) {
  if (el.matches(FIELD_SELECTOR)) return true;
  if (el.getAttribute("aria-haspopup") === "listbox") return true;
  if (/select|dropdown/i.test(el.className)) return true;
  const text = (el.innerText || "").trim().split("\n")[0];
  return /^(select|choose)$/i.test(text);
}

function stripLabel(text: string) {
  return text
    .replace(/\(optional\)/gi, "")
    .replace(/\*+/g, "")
    .replace(/\s+/g, " ")
    .trim();
}

function ownLabel(node: HTMLElement) {
  if (node.querySelector("input, textarea, select, [role='combobox'], table")) return "";
  const fromText = Array.from(node.childNodes)
    .filter((child) => child.nodeType === Node.TEXT_NODE)
    .map((child) => stripLabel(child.textContent || ""))
    .find((text) => text.length >= 2 && text.length <= 56);
  if (fromText) return fromText;
  if (node.children.length && node.children.length <= 6 && !node.querySelector(FIELD_SELECTOR)) {
    const joined = stripLabel(
      Array.from(node.childNodes)
        .map((child) => (child.textContent || "").replace(/\s+/g, " ").trim())
        .filter(Boolean)
        .join(" "),
    );
    const first = joined.split("\n")[0]?.trim() || "";
    if (first.length >= 2 && first.length <= 56 && !/[|]/.test(first)) return first;
  }
  if (node.children.length > 2) return "";
  const text = stripLabel(node.innerText || node.textContent || "");
  const first = text.split("\n")[0]?.trim() || "";
  if (first.length >= 2 && first.length <= 56 && !/[|]/.test(first)) return first;
  return "";
}

function firstVisible(scope: HTMLElement, selector: string) {
  return Array.from(scope.querySelectorAll<HTMLElement>(selector)).find((el) => visible(el) && !el.closest("#cs-sidebar")) || null;
}

function inferType(el: HTMLElement, label: string): FillStep["type"] {
  if (el instanceof HTMLInputElement && el.type === "checkbox") return "checkbox";
  if (el instanceof HTMLTextAreaElement || /description|address|details/i.test(label)) return "textarea";
  if (
    el instanceof HTMLSelectElement
    || el.getAttribute("role") === "combobox"
    || /gst|hsn|color|fabric|pattern|size|origin|sleeve|neck|fit|occasion|combo|quantity|stitch|length|brand|pack|category|closure|rise|type|wash|styling|multipack|material|unit/i.test(label)
  ) {
    return "select";
  }
  return "text";
}

export function planMeeshoFill(listing: MappedListing): FillStep[] {
  const values = listingFieldValues(listing);
  const discovered = discoverPageFields();
  const steps: FillStep[] = [];
  const used = new Set<HTMLElement>();
  for (const field of discovered) {
    if (used.has(field.element)) continue;
    if (normalizeKey(field.label) === "size" && detectSelectedPageSizes().length > 1) continue;
    const value = matchFieldValue(field.label, values);
    if (!value) continue;
    used.add(field.element);
    steps.push({
      hint: { labelText: field.label } satisfies SelectorHint,
      value,
      type: field.type === "select" && !looksLikeDropdown(field.element) ? "text" : field.type,
      element: field.element,
    });
  }
  for (const extra of meeshoSteps(listing)) {
    const key = normalizeKey(extra.hint.labelText || "");
    if (steps.some((step) => normalizeKey(step.hint.labelText || "") === key)) continue;
    if (extra.type === "checkbox") continue;
    if (key === "size" && detectSelectedPageSizes().length > 1) continue;
    const found = findField(extra.hint);
    if (!found || used.has(found.element) || found.element.closest("table") || isSizeGridField(found.element)) continue;
    used.add(found.element);
    steps.push({ ...extra, element: found.element });
  }
  const copy = checkboxStep(listing, "Copy input details to all product");
  const sameAs = checkboxStep(listing, "Same as Manufacturer Details");
  const hsn = steps.filter((step) => isHsnLabel(step.hint.labelText || ""));
  const gst = steps.filter((step) => isGstLabel(step.hint.labelText || ""));
  const rest = steps.filter((step) => !isHsnLabel(step.hint.labelText || "") && !isGstLabel(step.hint.labelText || ""));
  return [...(copy ? [copy] : []), ...hsn, ...rest, ...gst, ...(sameAs ? [sameAs] : [])];
}

function isHsnLabel(label: string) {
  return /\bhsn\b/i.test(label);
}

function isGstLabel(label: string) {
  return /\bgst\b|tax rate/i.test(label) && !/\bhsn\b/i.test(label);
}

function checkboxStep(listing: MappedListing, label: string) {
  return meeshoSteps(listing).find((step) => step.type === "checkbox" && normalizeKey(step.hint.labelText || "") === normalizeKey(label));
}

function looksLikeDropdown(el: HTMLElement) {
  if (el instanceof HTMLSelectElement || el.getAttribute("role") === "combobox") return true;
  if (el.getAttribute("aria-haspopup") === "listbox") return true;
  if (el.closest("[class*='select'], [class*='Select'], [class*='dropdown']")) return true;
  const text = (el.innerText || (el as HTMLInputElement).placeholder || "").trim().split("\n")[0];
  return /^(select|choose)$/i.test(text);
}

export function sizeAliases(size: string) {
  const cleaned = size.replace(/[()]/g, " ").replace(/\s+/g, " ").trim();
  const parts = cleaned.split(" ").filter(Boolean);
  const aliases = new Set<string>([size.trim(), cleaned, ...parts]);
  for (const part of [...parts]) {
    const alpha = ALPHA_FROM_NUMBER[part] || (ALPHA_SIZES.includes(part.toUpperCase()) ? part.toUpperCase() : "");
    const number = Object.entries(ALPHA_FROM_NUMBER).find(([, letter]) => letter === part.toUpperCase())?.[0];
    if (alpha) {
      aliases.add(alpha);
      aliases.add(`${alpha} (${number || part})`);
    }
    if (number) aliases.add(number);
    if (part.toUpperCase() === "XXL") aliases.add("2XL");
    if (part.toUpperCase() === "2XL") aliases.add("XXL");
  }
  return [...aliases];
}

function collectSizeLabel(raw: string, found: string[], seen: Set<string>) {
  for (const text of parseSizeList(raw)) {
    if (seen.has(text)) continue;
    seen.add(text);
    found.push(text);
  }
}

function parseSizeList(raw: string) {
  return raw
    .split(/[,|/]+/)
    .map((part) => part.replace(/\s+/g, " ").trim())
    .filter((part) => isSizeLabel(part));
}

export function detectPageSizes() {
  const found: string[] = [];
  const seen = new Set<string>();
  const nodes = Array.from(document.querySelectorAll<HTMLElement>("button, [role='checkbox'], [role='option'], [role='radio'], label, li, [class*='chip'], [class*='size']"));
  for (const el of nodes) {
    if (el.closest("#cs-sidebar") || !visible(el)) continue;
    collectSizeLabel((el.innerText || el.textContent || "").split("\n")[0], found, seen);
  }
  for (const option of Array.from(document.querySelectorAll("select option"))) {
    if (option.closest("#cs-sidebar")) continue;
    collectSizeLabel(option.textContent || option.value || "", found, seen);
  }
  for (const table of Array.from(document.querySelectorAll("table"))) {
    if (table.closest("#cs-sidebar")) continue;
    const headers = headerMap(table);
    const rows = Array.from(table.querySelectorAll("tbody tr, tr"));
    for (const row of rows) {
      collectSizeLabel(rowSize(row, headers), found, seen);
    }
  }
  return found;
}

export function detectSelectedPageSizes() {
  const found: string[] = [];
  const seen = new Set<string>();
  for (const table of Array.from(document.querySelectorAll("table"))) {
    if (table.closest("#cs-sidebar")) continue;
    const headers = headerMap(table);
    const rows = Array.from(table.querySelectorAll("tbody tr, tr")).filter((row) => row.querySelector("input, textarea, select, [role='combobox']"));
    for (const row of rows) {
      collectSizeLabel(rowSize(row, headers), found, seen);
    }
  }
  for (const select of Array.from(document.querySelectorAll("select"))) {
    if (select.closest("#cs-sidebar")) continue;
    for (const option of Array.from(select.selectedOptions)) {
      collectSizeLabel(option.textContent || option.value || "", found, seen);
    }
    collectSizeLabel(select.value || "", found, seen);
  }
  for (const combo of Array.from(document.querySelectorAll<HTMLElement>("[role='combobox']"))) {
    if (combo.closest("#cs-sidebar") || !visible(combo)) continue;
    collectSizeLabel((combo.innerText || combo.textContent || "").replace(/\n/g, " "), found, seen);
  }
  const chips = Array.from(document.querySelectorAll<HTMLElement>("button, [role='checkbox'], [role='option'], [role='radio'], label, li, [class*='chip'], [class*='size']"));
  for (const el of chips) {
    if (el.closest("#cs-sidebar") || !visible(el)) continue;
    const text = (el.innerText || el.textContent || "").split("\n")[0].trim();
    if (!isSizeLabel(text) || !isSizeControlSelected(el)) continue;
    collectSizeLabel(text, found, seen);
  }
  return found;
}

export function isSizeControlSelected(el: HTMLElement) {
  const host = `${el.className} ${el.parentElement?.className || ""} ${el.getAttribute("aria-pressed") || ""} ${el.getAttribute("aria-selected") || ""}`;
  if (el.getAttribute("aria-pressed") === "true" || el.getAttribute("aria-checked") === "true" || el.getAttribute("aria-selected") === "true") {
    return true;
  }
  if (/\b(selected|active|checked|Mui-selected)\b/i.test(host)) return true;
  const box =
    el instanceof HTMLInputElement && (el.type === "checkbox" || el.type === "radio")
      ? el
      : el.querySelector<HTMLInputElement>("input[type='checkbox'], input[type='radio']");
  if (box?.checked) return true;
  return isHighlightedChip(el);
}

function isHighlightedChip(el: HTMLElement) {
  try {
    const style = window.getComputedStyle(el);
    const match = style.backgroundColor.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/i);
    if (!match) return false;
    const red = Number(match[1]);
    const green = Number(match[2]);
    const blue = Number(match[3]);
    if (red > 240 && green > 240 && blue > 240) return false;
    if (red + green + blue < 50) return false;
    return (red > 90 && blue > 80 && red - green > 8) || (green > 90 && blue > 80 && green - red > 20);
  } catch {
    return false;
  }
}

export function detectSizeChips() {
  return detectPageSizes().filter((text) => SIZE_TOKEN.has(text.split("(")[0].trim()) || isSizeLabel(text));
}

function isSizeLabel(text: string) {
  if (!text || text.length > 18) return false;
  if (SIZE_TOKEN.has(text)) return true;
  return /^(XS|S|M|L|XL|XXL|2XL|3XL|4XL|5XL|6XL|7XL|8XL|Free Size|Semi Stitched)(\s*\(\d+\))?$/i.test(text)
    || /^(2[8-9]|3[0-9]|4[0-6])$/.test(text);
}

export function fallbackSizesForListing(listing: MappedListing) {
  const blob = `${listing.title || ""} ${listing.genericName || ""} ${listing.mainCategory || ""} ${listing.size || ""}`.toLowerCase();
  if (/plus size/.test(blob) && /set/.test(blob)) return ["XL", "2XL", "3XL", "4XL", "5XL", "6XL"];
  if (/plus size|3xl|4xl|5xl|6xl/.test(blob)) return ["M", "L", "XL", "XXL", "3XL", "4XL", "5XL", "6XL"];
  if (/pant|trouser|jean|palazzo|lower/.test(blob)) return PANT_SIZES;
  if (/kurti fabric|unstitched|semi stitched/.test(blob)) return listing.size ? [listing.size] : ["Semi Stitched"];
  if (/\bkurti|\bkurtas?\b|\bdress|\bgown/.test(blob)) return ["M", "L", "XL", "XXL"];
  if (/\bt-?shirt|\btee\b|\btunic|\btops?\b|\bshirts?\b/.test(blob)) return FALLBACK_APPAREL;
  return listing.size ? [listing.size] : FALLBACK_APPAREL;
}

export function sizesForListing(listing: MappedListing) {
  if (listing.selectedSizes?.length) return listing.selectedSizes;
  const selected = detectSelectedPageSizes();
  if (selected.length) return selected;
  const onPage = detectPageSizes();
  if (onPage.length) return onPage;
  return fallbackSizesForListing(listing);
}

export function measuresForSize(size: string, listing: MappedListing): SizeMeasures | null {
  const key = chartKey(size);
  const blob = `${listing.gender || ""} ${listing.title || ""} ${listing.genericName || ""} ${listing.mainCategory || ""}`.toLowerCase();
  if (/pant|trouser|jean|palazzo/.test(blob) && /^\d+$/.test(size)) return pantMeasures(size);
  if (/\bt-?shirt|\btee\b/.test(blob)) return TSHIRT_CHART[key] || numericShirtMeasures(size);
  if (/\bshirts?\b/.test(blob) && !/\bt-?shirts?\b/.test(blob)) {
    if (/\bwom[ae]n|ladies|girl|female/.test(blob)) return TOP_CHART[key] || TSHIRT_CHART[key] || numericShirtMeasures(size);
    return TSHIRT_CHART[key] || TOP_CHART[key] || numericShirtMeasures(size);
  }
  if (/\btunic|\btops?\b/.test(blob)) return TOP_CHART[key] || TSHIRT_CHART[key] || numericShirtMeasures(size);
  if (/\bkurti|\bkurta|\bsaree|\bdress|\bgown/.test(blob)) return KURTI_CHART[key] || TOP_CHART[key] || numericShirtMeasures(size);
  return TOP_CHART[key] || TSHIRT_CHART[key] || KURTI_CHART[key] || pantMeasures(size);
}

function chartKey(size: string) {
  const cleaned = size.replace(/[()]/g, " ").replace(/\s+/g, " ").trim();
  const token = cleaned.split(" ").find((part) => ALPHA_SIZES.includes(part.toUpperCase()) || ALPHA_FROM_NUMBER[part]) || cleaned;
  if (ALPHA_FROM_NUMBER[token]) return ALPHA_FROM_NUMBER[token];
  return token.toUpperCase();
}

export async function fillSizeChoices(listing: MappedListing, onProgress?: (message: string) => void) {
  const sizes = listing.selectedSizes?.length ? listing.selectedSizes : sizesForListing(listing);
  const results: FillResult[] = [];
  for (const size of sizes) {
    onProgress?.(`Selecting size ${size}...`);
    const clicked = clickSizeChip(size);
    if (clicked) {
      results.push({ field: `Size ${size}`, ok: true, message: "Selected" });
      await delay(180);
      continue;
    }
    const box = await setCheckbox({ labelText: size }, true);
    results.push({ field: `Size ${size}`, ok: box.ok, message: box.message });
  }
  return results;
}

export function clickSizeChip(size: string, toggle = false) {
  const aliases = sizeAliases(size).map((item) => normalizeKey(item));
  const nodes = Array.from(document.querySelectorAll<HTMLElement>("button, [role='checkbox'], [role='option'], [role='radio'], span, div, label, li"));
  const match = nodes.find((el) => {
    if (el.closest("#cs-sidebar")) return false;
    if (!visible(el)) return false;
    const text = normalizeKey((el.innerText || el.textContent || "").split("\n")[0]);
    if (!text || text.length > 18) return false;
    return aliases.includes(text);
  });
  if (!match) return false;
  const selected = isSizeControlSelected(match);
  if (!selected || toggle) match.click();
  return true;
}

const SIZE_CELL_IDS = new Set(["meesho_price", "only_wrong_return_price", "product_mrp", "inventory", "supplier_sku_id"]);

function isSizeGridField(el: HTMLElement) {
  const id = (el.id || "").toLowerCase();
  if (SIZE_CELL_IDS.has(id)) return true;
  return Boolean(el.closest("[id='meesho_price'], [id='product_mrp']")?.parentElement?.querySelector("[id='meesho_price'], [id='product_mrp']"));
}

export async function fillSizeChart(listing: MappedListing, onProgress?: (message: string) => void) {
  closeOpenMenus();
  await waitForSizeRows(2200);
  const results: FillResult[] = [];
  const skuBase = (listing.skuId || listing.styleCode || "SKU").replace(/\s+/g, "-").replace(/-+$/, "");
  for (const row of collectSizeRows()) {
    onProgress?.(`Filling size ${row.size}...`);
    results.push(...(await fillVariationRow(row.element, row.headers, listing, row.size, skuBase)));
  }
  closeOpenMenus();
  return results.filter((item) => item.field);
}

async function waitForSizeRows(timeoutMs: number) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (collectSizeRows().length) return;
    await delay(120);
  }
}

function collectSizeRows() {
  const rows: Array<{ size: string; element: HTMLElement; headers: Map<number, string> }> = [];
  const seen = new Set<HTMLElement>();
  const add = (size: string, element: HTMLElement, headers: Map<number, string>) => {
    if (!size || seen.has(element)) return;
    if ([...seen].some((existing) => existing.contains(element) || element.contains(existing))) return;
    seen.add(element);
    rows.push({ size, element, headers });
  };
  for (const table of Array.from(document.querySelectorAll("table"))) {
    if (table.closest("#cs-sidebar")) continue;
    const headers = headerMap(table);
    if (!headers.size) continue;
    const tableRows = Array.from(table.querySelectorAll("tbody tr, tr")).filter((row) => row.querySelector("input, textarea, select, [role='combobox']"));
    for (const row of tableRows) {
      add(rowSize(row, headers) || "M", row as HTMLElement, headers);
    }
  }
  for (const row of findDivSizeRows()) {
    add(row.size, row.element, new Map());
  }
  return rows;
}

function findDivSizeRows() {
  const rows: Array<{ size: string; element: HTMLElement }> = [];
  const seen = new Set<HTMLElement>();
  const seenSize = new Set<string>();
  const anchors = Array.from(
    document.querySelectorAll<HTMLElement>(
      "[id='meesho_price'], [id='product_mrp'], [id='supplier_sku_id'], [id='inventory'], [id='shoulder_size'], [id='waist_size'], [id='length_size'], [id='hip_size'], [id='bust_size']",
    ),
  ).filter((el) => !el.closest("#cs-sidebar") && visible(el));
  for (const anchor of anchors) {
    const element = closestSingleSizeRow(anchor);
    if (!element || seen.has(element)) continue;
    const size = sizeLabelIn(element);
    const key = normalizeKey(size);
    if (!size || seenSize.has(key)) continue;
    seen.add(element);
    seenSize.add(key);
    rows.push({ size, element });
  }
  if (rows.length) return rows;
  const labels = Array.from(document.querySelectorAll<HTMLElement>("p, span, div, h6, td, th")).filter((el) => {
    if (el.closest("#cs-sidebar") || el.children.length || !visible(el)) return false;
    return isSizeLabel((el.textContent || "").trim());
  });
  for (const label of labels) {
    const element = closestSingleSizeRow(label);
    if (!element || seen.has(element)) continue;
    const nums = element.querySelectorAll("input");
    if (nums.length < 3) continue;
    const size = (label.textContent || "").trim().split("\n")[0];
    const key = normalizeKey(size);
    if (!size || seenSize.has(key)) continue;
    seen.add(element);
    seenSize.add(key);
    rows.push({ size, element });
  }
  return rows;
}

function closestSingleSizeRow(el: HTMLElement) {
  let node: HTMLElement | null = el;
  let best: HTMLElement | null = null;
  for (let i = 0; i < 12 && node; i++) {
    if (node.id === "cs-sidebar") break;
    const prices = node.querySelectorAll("[id='meesho_price']").length;
    const mrps = node.querySelectorAll("[id='product_mrp']").length;
    const measures = node.querySelectorAll("[id$='_size']").length;
    if (prices > 1 || mrps > 1 || measures > 6) break;
    const inputs = node.querySelectorAll("input").length;
    if ((prices === 1 || mrps === 1 || inputs >= 3) && inputs >= 2) best = node;
    node = node.parentElement;
  }
  return best;
}

function sizeLabelIn(row: HTMLElement) {
  const nodes = Array.from(row.querySelectorAll<HTMLElement>("p, span, div, h6, td, th"));
  const match = nodes.find((el) => {
    if (el.closest("#cs-sidebar") || el.querySelector("input")) return false;
    return isSizeLabel((el.textContent || "").trim().split("\n")[0]);
  });
  return (match?.textContent || "").trim().split("\n")[0] || "";
}

async function fillVariationRow(row: Element, headers: Map<number, string>, listing: MappedListing, size: string, skuBase: string) {
  const results: FillResult[] = [];
  const measures = measuresForSize(size, listing);
  const money = sizeMoney(listing);
  const sku = `${skuBase}-${chartKey(size)}`.slice(0, 40);
  results.push(await fillRowField(row, headers, { ids: ["meesho_price"], names: ["listing price", "meesho price", "selling price"], fallbackIndex: 0, value: money.selling, field: "meesho price" }));
  results.push(await fillRowField(row, headers, { ids: ["only_wrong_return_price"], names: ["wrong", "defective", "returns price", "return price"], fallbackIndex: 1, value: money.returns, field: "returns price" }));
  results.push(await fillRowField(row, headers, { ids: ["product_mrp"], names: ["mrp", "maximum retail"], fallbackIndex: 2, value: money.mrp, field: "mrp" }));
  results.push(await fillRowField(row, headers, { ids: ["inventory"], names: ["inventory", "qty", "quantity", "stock"], fallbackIndex: 3, value: listing.inventory || "5", field: "inventory" }));
  results.push(await fillRowField(row, headers, { ids: ["supplier_sku_id"], names: ["sku id", "sku", "style code"], value: sku, field: "sku id" }));
  if (!headerHas(headers, ["listing price", "meesho price", "selling price"]) && !row.querySelector("[id='meesho_price']")) {
    results.push(await fillRowField(row, headers, { ids: [], names: ["price"], value: money.selling, field: "price" }));
  }
  if (measures) {
    results.push(await fillRowField(row, headers, { ids: ["shoulder_size"], names: ["shoulder size", "shoulder"], value: measures.shoulder, field: "shoulder" }));
    results.push(await fillRowField(row, headers, { ids: ["length_size"], names: ["size length", "length size", "length"], value: measures.length, field: "length" }));
    results.push(await fillRowField(row, headers, { ids: ["bust_size", "top_chest_size"], names: ["breast", "bust", "chest"], value: measures.bust, field: "bust" }));
    results.push(await fillRowField(row, headers, { ids: ["waist_size"], names: ["waist size", "waist"], value: measures.waist, field: "waist" }));
    results.push(await fillRowField(row, headers, { ids: ["hip_size"], names: ["hip size", "hip", "hips"], value: measures.hip, field: "hip" }));
  }
  return results;
}

function sizeMoney(listing: MappedListing) {
  const selling = parseMoney(listing.sellingPrice) || parseMoney(listing.mrp) || 499;
  const mrp = parseMoney(listing.mrp) || Math.max(selling, Math.round(selling * 2));
  return { selling: String(selling), mrp: String(mrp), returns: String(Math.max(1, selling - 1)) };
}

function parseMoney(value?: string) {
  const amount = Number(String(value || "").replace(/[^\d.]/g, ""));
  return Number.isFinite(amount) && amount > 0 ? amount : 0;
}

async function fillRowField(
  row: Element,
  headers: Map<number, string>,
  options: { ids: string[]; names: string[]; fallbackIndex?: number; value: string; field: string },
): Promise<FillResult> {
  if (!options.value) return { field: "", ok: false, message: "skip" };
  const input = findRowInput(row, headers, options);
  if (!input) return { field: options.field, ok: false, message: "Input not found" };
  if (input instanceof HTMLSelectElement) {
    return selectNativeDropdown({ labelText: options.field }, options.value, input);
  }
  if (input.getAttribute("role") === "combobox" || isDropdownElement(input)) {
    return selectCustomDropdown({ labelText: options.field }, options.value, input);
  }
  if (input instanceof HTMLInputElement && input.type === "number" && !/^-?\d+(\.\d+)?$/.test(options.value)) {
    return { field: options.field, ok: false, message: "Skipped non-numeric value" };
  }
  triggerReactInputEvents(input, options.value);
  const ok = verifyFieldValue(input, options.value);
  return { field: options.field, ok, message: ok ? "Filled" : "Value not accepted" };
}

function findRowInput(row: Element, headers: Map<number, string>, options: { ids: string[]; names: string[]; fallbackIndex?: number }) {
  for (const id of options.ids) {
    const byId = row.querySelector<HTMLElement>(`[id="${id}"]`);
    if (byId) return byId;
  }
  const byName = controlMatchingNames(row, options.names);
  if (byName) return byName;
  const exact = [...headers.entries()].find(([, label]) => options.names.some((name) => label === name));
  const fuzzy = [...headers.entries()].find(([, label]) => options.names.some((name) => label.includes(name)));
  const index = (exact || fuzzy)?.[0];
  if (index != null) {
    const cell = row.children[index] as HTMLElement | undefined;
    const input = cell?.querySelector<HTMLElement>("input, textarea, select, [role='combobox']");
    if (input) return input;
  }
  if (options.fallbackIndex == null || headers.size) return null;
  return numberedRowInputs(row)[options.fallbackIndex] || null;
}

function controlMatchingNames(row: Element, names: string[]) {
  const controls = Array.from(row.querySelectorAll<HTMLElement>("input, select, [role='combobox'], [aria-haspopup='listbox']"));
  return controls.find((el) => {
    const blob = normalizeKey(`${el.id} ${el.getAttribute("aria-label") || ""} ${el.getAttribute("name") || ""} ${el.placeholder || ""}`);
    return names.some((name) => blob.includes(name.replace(/\s+/g, "_")) || blob.includes(name));
  }) || null;
}

function numberedRowInputs(row: Element) {
  return Array.from(row.querySelectorAll<HTMLInputElement>("input")).filter((el) => {
    if (el.closest("#cs-sidebar")) return false;
    const id = (el.id || "").toLowerCase();
    if (id === "supplier_sku_id") return false;
    return el.type === "number" || el.type === "text" || el.type === "";
  });
}

export async function fillSelectedSize(listing: MappedListing, size: string, onProgress?: (message: string) => void) {
  onProgress?.(`Selecting size ${size}...`);
  const selected = clickSizeChip(size);
  const scoped = { ...listing, selectedSizes: [size], size };
  const chart = await fillSizeChart(scoped, onProgress);
  return [{ field: `Size ${size}`, ok: selected, message: selected ? "Selected" : "Size control not found" }, ...chart];
}

function headerHas(headers: Map<number, string>, names: string[]) {
  return [...headers.values()].some((label) => names.some((name) => label === name || label.includes(name)));
}

function sizeMatchesSelection(size: string, selected: string[]) {
  const aliases = new Set(selected.flatMap((item) => sizeAliases(item).map((alias) => normalizeKey(alias))));
  return sizeAliases(size).some((alias) => aliases.has(normalizeKey(alias)));
}

function headerMap(table: HTMLTableElement) {
  const map = new Map<number, string>();
  const headerRow = table.querySelector("thead tr") || table.querySelector("tr");
  if (!headerRow) return map;
  Array.from(headerRow.children).forEach((cell, index) => {
    const text = normalizeKey(cell.textContent || "");
    if (text) map.set(index, text);
  });
  return map;
}

function rowSize(row: Element, headers: Map<number, string>) {
  const sizeIndex = [...headers.entries()].find(([, label]) => label === "size" || label.startsWith("size") || label === "variation")?.[0];
  if (sizeIndex != null) {
    const cell = row.children[sizeIndex] as HTMLElement | undefined;
    const fromControl = controlValue(cell);
    if (fromControl) return fromControl;
    const text = (cell?.textContent || "").trim();
    if (text) return text.split("\n")[0].trim();
  }
  const firstCell = row.querySelector("td, th") as HTMLElement | null;
  return controlValue(firstCell) || (firstCell?.textContent || "").trim().split("\n")[0] || "";
}

function controlValue(cell: HTMLElement | undefined | null) {
  if (!cell) return "";
  const select = cell.querySelector("select");
  if (select) {
    const option = select.selectedOptions[0];
    return (option?.textContent || select.value || "").trim();
  }
  const input = cell.querySelector("input");
  if (input?.value) return input.value.trim();
  const combo = cell.querySelector("[role='combobox']");
  if (combo) return (combo.textContent || "").trim().split("\n")[0];
  return "";
}

function pantMeasures(size: string): SizeMeasures | null {
  if (!/^\d+$/.test(size)) return null;
  return { length: "38", bust: "", waist: size, hip: String(Number(size) + 8), shoulder: "" };
}

function numericShirtMeasures(size: string): SizeMeasures | null {
  const alpha = ALPHA_FROM_NUMBER[size];
  if (alpha && TSHIRT_CHART[alpha]) return TSHIRT_CHART[alpha];
  return pantMeasures(size);
}

function delay(ms: number) {
  return Promise.resolve().then(() => new Promise((resolve) => setTimeout(resolve, ms)));
}
