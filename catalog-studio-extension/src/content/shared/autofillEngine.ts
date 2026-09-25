import { findCheckbox, findField, type SelectorHint, visible, waitForField } from "./fieldFinder";

export type FillResult = { field: string; ok: boolean; message: string };

let stopped = false;

export function stopAutofill() {
  stopped = true;
}

export function resetAutofill() {
  stopped = false;
}

export function isStopped() {
  return stopped;
}

export function triggerReactInputEvents(el: HTMLElement, value: string) {
  if (el instanceof HTMLInputElement && el.type === "number" && !isNumericString(value)) {
    return;
  }
  try {
    el.focus();
  } catch {
    // not focusable
  }
  const proto = el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  const native = el as HTMLInputElement;
  const tracker = (native as HTMLInputElement & { _valueTracker?: { setValue: (v: string) => void } })._valueTracker;
  tracker?.setValue("");
  try {
    const setter = Object.getOwnPropertyDescriptor(proto, "value")?.set;
    setter?.call(el, value);
  } catch {
    if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) {
      el.value = value;
    }
  }
  if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) {
    el.value = value;
  }
  el.dispatchEvent(new Event("input", { bubbles: true }));
  el.dispatchEvent(new Event("change", { bubbles: true }));
  try {
    el.dispatchEvent(new InputEvent("input", { bubbles: true, data: value, inputType: "insertText" }));
  } catch {
    // jsdom or older browsers
  }
}

export function isDropdownElement(el: HTMLElement) {
  if (el instanceof HTMLSelectElement) return true;
  if (el instanceof HTMLTextAreaElement) return false;
  if (el instanceof HTMLInputElement) {
    if (el.readOnly || el.hasAttribute("readonly")) return true;
    if (/^(select|choose)$/i.test(el.placeholder || el.value || "")) return true;
    const host = ((el.closest("[class*='select'], [class*='Select'], [class*='dropdown']") as HTMLElement | null)?.innerText || "").trim().split("\n")[0];
    if (/^(select|choose)$/i.test(host)) return true;
    return false;
  }
  if (el.getAttribute("role") === "combobox" || el.getAttribute("aria-haspopup") === "listbox") return true;
  const text = (el.innerText || "").trim().split("\n")[0];
  return /^(select|choose)$/i.test(text) && text.length < 20;
}

function isNumericString(value: string) {
  return /^-?\d+(\.\d+)?$/.test(value.trim());
}

function dropdownCandidates(value: string) {
  const unique: string[] = [];
  const add = (item: string) => {
    const text = item.trim();
    if (text && !unique.some((existing) => existing.toLowerCase() === text.toLowerCase())) unique.push(text);
  };
  add(value);
  const lower = value.toLowerCase();
  if (/^\d+(\.\d+)?$/.test(value.trim())) {
    add(`${value.trim()}%`);
    add(`${value.trim()} %`);
  }
  if (/^\d+(\.\d+)?\s*%$/.test(value.trim())) {
    add(value.replace(/\s*%$/, "").trim());
  }
  if (lower === "regular") {
    add("Regular Fit");
    add("Regular Length");
  }
  if (lower === "regular fit" || lower === "regular length") add("Regular");
  if (lower === "crop") add("Cropped");
  if (lower === "cropped") add("Crop");
  if (/bell/.test(lower)) {
    add("Three-Quarter Sleeves");
    add("Long Sleeves");
    add("Bell Sleeves");
  }
  if (lower === "casual") {
    add("Daily");
    add("Western");
  }
  if (lower === "daily") add("Casual");
  if (lower === "party") add("Festive");
  if (lower === "festive") add("Party");
  if (/three[-\s]?quarter/.test(lower)) {
    add("Three-Quarter Sleeves");
    add("Three Quarter Sleeves");
    add("3/4th Sleeves");
    add("Three-Quarter Sleeve");
    add("3/4 Sleeves");
  }
  if (lower === "v-neck" || lower === "v neck") {
    add("V-Neck");
    add("V-neck");
    add("V Neck");
  }
  if (lower === "na" || lower === "n/a" || lower === "none") {
    add("None");
    add("Not Applicable");
    add("NA");
  }
  if (lower === "not applicable") add("None");
  if (/sleeves$/.test(lower)) add(value.replace(/sleeves$/i, "Sleeve"));
  if (/sleeve$/.test(lower)) add(`${value}s`);
  return unique;
}

function realClick(el: HTMLElement) {
  try {
    el.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    el.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
  } catch {
    // jsdom without full mouse events
  }
  el.click();
}

export function closeOpenMenus() {
  fireEscape(document);
  fireEscape(document.body);
  for (const node of openMenuNodes()) {
    fireEscape(node);
    if (node.parentElement) fireEscape(node.parentElement);
  }
  for (const backdrop of document.querySelectorAll<HTMLElement>(".MuiBackdrop-root, [class*='backdrop'], [class*='overlay']")) {
    if (backdrop.closest("#cs-sidebar") || !visible(backdrop)) continue;
    backdrop.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    backdrop.click();
  }
  try {
    document.body.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
  } catch {
    // jsdom
  }
}

const MENU_ROOT = ".MuiPopover-root, .MuiModal-root, .MuiMenu-root, [role='listbox'], [role='menu'], [class*='popover'], [class*='Popover'], [class*='dropdown-menu']";

function openMenuNodes() {
  return Array.from(document.querySelectorAll<HTMLElement>(MENU_ROOT)).filter((node) => !node.closest("#cs-sidebar") && visible(node));
}

function menuIsOpen() {
  if (openMenuNodes().length) return true;
  return Array.from(document.querySelectorAll<HTMLElement>("[role='option'], li[class*='MenuItem']")).some(
    (node) => !node.closest("#cs-sidebar") && visible(node),
  );
}

function fireEscape(target: EventTarget | null) {
  if (!target) return;
  const init = { key: "Escape", code: "Escape", keyCode: 27, which: 27, bubbles: true, cancelable: true };
  try {
    target.dispatchEvent(new KeyboardEvent("keydown", init));
    target.dispatchEvent(new KeyboardEvent("keyup", init));
  } catch {
    // older event targets
  }
}

export async function closeMenus() {
  for (let attempt = 0; attempt < 6 && menuIsOpen(); attempt++) {
    closeOpenMenus();
    await delay(attempt < 3 ? 20 : 40);
  }
  return !menuIsOpen();
}

export function menuOptionsOf(el: HTMLElement | null | undefined) {
  const host = el?.closest?.("[menuoptions]") || (el?.hasAttribute?.("menuoptions") ? el : null);
  const raw = host?.getAttribute("menuoptions") || "";
  const options = raw.split(",").map((item) => item.trim()).filter(Boolean);
  return options.length ? options : null;
}

function optionNumber(value: string) {
  const match = normalize(value).match(/^(\d+(?:\.\d+)?)\s*(?:in|inch|inches|cm|cms|kg|kgs|gms?|%|")?$/);
  return match ? Number(match[1]) : null;
}

export function snapToOptions(value: string, options: string[], exactOnly = false) {
  const want = normalize(value);
  if (!want) return null;
  const exact = options.find((option) => normalize(option) === want);
  if (exact) return exact;
  if (exactOnly) return null;
  const wantNumber = optionNumber(want);
  if (wantNumber != null) {
    return options.find((option) => optionNumber(option) === wantNumber) || null;
  }
  return options.find((option) => {
    const text = normalize(option);
    return optionNumber(text) == null && text.includes(want);
  }) || null;
}

function scrollField(el: HTMLElement) {
  try {
    el.scrollIntoView({ block: "center", inline: "nearest" });
  } catch {
    // jsdom or cross-origin frame
  }
}

export async function fillTextInput(hint: SelectorHint, value: string, element?: HTMLElement): Promise<FillResult> {
  if (stopped) return { field: hint.labelText || "field", ok: false, message: "Stopped" };
  let match: { element: HTMLElement; label: string };
  try {
    match = element ? { element, label: hint.labelText || "field" } : await waitForField(hint, 1500);
  } catch {
    return { field: hint.labelText || "field", ok: false, message: "Field not on this form" };
  }
  scrollField(match.element);
  await delay(80);
  if (isDropdownElement(match.element)) {
    return selectCustomDropdown(hint, value, match.element);
  }
  if (match.element instanceof HTMLInputElement && match.element.type === "number" && !isNumericString(value)) {
    return { field: match.label, ok: false, message: "Skipped non-numeric value" };
  }
  if (match.element.getAttribute("contenteditable") === "true") {
    match.element.focus();
    match.element.textContent = value;
    match.element.dispatchEvent(new InputEvent("input", { bubbles: true, data: value }));
  } else {
    triggerReactInputEvents(match.element, value);
    try {
      match.element.dispatchEvent(new Event("blur", { bubbles: true }));
    } catch {
      // not focusable
    }
  }
  const ok = verifyFieldValue(match.element, value);
  return { field: match.label, ok, message: ok ? "Filled" : "Value not accepted" };
}

export async function fillTextArea(hint: SelectorHint, value: string, element?: HTMLElement) {
  return fillTextInput(hint, value, element);
}

export async function selectNativeDropdown(hint: SelectorHint, value: string, element?: HTMLElement, exactOnly = false): Promise<FillResult> {
  if (stopped) return { field: hint.labelText || "select", ok: false, message: "Stopped" };
  let match: { element: HTMLElement; label: string };
  try {
    match = element ? { element, label: hint.labelText || "select" } : await waitForField(hint, 1500);
  } catch {
    return { field: hint.labelText || "select", ok: false, message: "Field not on this form" };
  }
  scrollField(match.element);
  await delay(80);
  if (!(match.element instanceof HTMLSelectElement)) {
    return selectCustomDropdown(hint, value, match.element, exactOnly);
  }
  const options = Array.from(match.element.options);
  const option = exactOnly
    ? options.find((o) => normalize(o.text || o.value) === normalize(value))
    : options.find((o) => optionTextMatches(o.text || o.value, value)) || nearestSelectOption(options, value);
  if (!option) {
    return { field: match.label, ok: false, message: "Dropdown value is unavailable" };
  }
  match.element.value = option.value;
  match.element.dispatchEvent(new Event("change", { bubbles: true }));
  return { field: match.label, ok: true, message: "Selected" };
}

export async function setNumberCell(el: HTMLElement, value: string) {
  const next = String(value);
  const write = () => {
    try {
      el.focus();
    } catch {
      // not focusable
    }
    el.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
    el.dispatchEvent(new MouseEvent("mouseup", { bubbles: true }));
    el.click();
    el.click();
    try {
      el.dispatchEvent(new MouseEvent("dblclick", { bubbles: true }));
    } catch {
      // jsdom
    }
    triggerReactInputEvents(el, "");
    triggerReactInputEvents(el, next);
  };
  write();
  if (cellValue(el) !== next) {
    await delay(60);
    if (cellValue(el) !== next) write();
    if (cellValue(el) !== next) {
      await delay(40);
      write();
    }
  }
  try {
    el.dispatchEvent(new Event("blur", { bubbles: true }));
  } catch {
    // not focusable
  }
  return cellValue(el) === next;
}

function cellValue(el: HTMLElement) {
  return "value" in el ? String((el as HTMLInputElement).value) : (el.textContent || "").trim();
}

export async function selectCustomDropdown(hint: SelectorHint, value: string, element?: HTMLElement, exactOnly = false): Promise<FillResult> {
  if (stopped) return { field: hint.labelText || "dropdown", ok: false, message: "Stopped" };
  let match: { element: HTMLElement; label: string };
  try {
    match = element ? { element, label: hint.labelText || "dropdown" } : await waitForField(hint, 1500);
  } catch {
    return { field: hint.labelText || "dropdown", ok: false, message: "Field not on this form" };
  }
  const listed = menuOptionsOf(match.element);
  let target = value;
  if (listed) {
    const snapped = dropdownCandidates(value).map((candidate) => snapToOptions(candidate, listed, exactOnly)).find(Boolean) || null;
    if (!snapped) {
      await closeMenus();
      return { field: match.label, ok: false, message: "Dropdown value is unavailable" };
    }
    target = snapped;
  }
  scrollField(match.element);
  await closeMenus();
  const opener =
    (match.element.closest("[class*='select'], [class*='Select'], [class*='dropdown'], [aria-haspopup='listbox']") as HTMLElement | null)
    || match.element;
  let picked = false;
  for (let attempt = 0; attempt < 2 && !picked; attempt++) {
    realClick(opener);
    const opened = await waitUntil(() => visibleMenuOptions().length > 0 || Boolean(menuSearchInput()), 900);
    if (!opened) continue;
    const search = menuSearchInput();
    if (search && search !== match.element) {
      triggerReactInputEvents(search, target.split(";")[0]);
      await waitUntil(() => Boolean(findMenuOption(target, true)), 500);
    }
    let option = findMenuOption(target, true) || (!listed && !exactOnly ? findMenuOption(target, false) : undefined);
    if (!option && !exactOnly) option = await scrollMenuFor(target);
    if (!option) {
      await closeMenus();
      continue;
    }
    try {
      option.scrollIntoView({ block: "center" });
    } catch {
      // jsdom
    }
    realClick(option);
    await delay(30);
    picked = dropdownValueStuck(match.element, target);
    await closeMenus();
  }
  if (!picked && !listed && !exactOnly && match.element instanceof HTMLInputElement && !match.element.readOnly) {
    picked = await setNumberCell(match.element, target);
  }
  return { field: match.label, ok: picked, message: picked ? "Selected" : "Dropdown value is unavailable" };
}

function dropdownValueStuck(el: HTMLElement, value: string) {
  const got = normalize("value" in el ? String((el as HTMLInputElement).value) : el.textContent || "");
  const want = normalize(value);
  if (!got || got === "select" || got === "choose") return false;
  if (got === want || got.includes(want)) return true;
  const gotNumber = optionNumber(got);
  const wantNumber = optionNumber(want);
  return gotNumber != null && wantNumber != null && gotNumber === wantNumber;
}

function menuSearchInput() {
  const roots = openMenuNodes();
  const scopes = roots.length ? roots : [];
  for (const root of scopes) {
    for (const input of root.querySelectorAll<HTMLInputElement>("input")) {
      if (input.closest("#cs-sidebar") || !visible(input)) continue;
      if ((input.placeholder || "").toLowerCase().includes("search")) return input;
    }
  }
  return null;
}

function visibleMenuOptions() {
  const roots = openMenuNodes();
  const scopes = roots.length ? roots : [document];
  const seen = new Set<HTMLElement>();
  const list: HTMLElement[] = [];
  for (const root of scopes) {
    for (const el of root.querySelectorAll<HTMLElement>("[role='option'], li, [class*='option'], [class*='MenuItem']")) {
      if (seen.has(el) || el.closest("#cs-sidebar") || !visible(el)) continue;
      if (el.querySelector("[role='option'], li")) continue;
      seen.add(el);
      list.push(el);
    }
  }
  return list;
}

function findMenuOption(value: string, exact: boolean) {
  const needle = normalize(value);
  const nodes = visibleMenuOptions().filter((el) => {
    const text = normalize(el.textContent || "");
    return text && text.length <= 80;
  });
  const exactHit = nodes.find((el) => optionTextMatches(el.textContent || "", value) && normalize(el.textContent || "") === needle)
    || nodes.find((el) => optionTextMatches(normalize(el.textContent || "").split(";")[0] || "", value));
  if (exactHit || exact) return exactHit;
  return nodes.find((el) => normalize(el.textContent || "").includes(needle)) || nearestNumericOption(nodes, value);
}

async function scrollMenuFor(value: string) {
  const hit = findMenuOption(value, true);
  if (hit) return hit;
  const sample = document.querySelector<HTMLElement>("li[class*='MenuItem'], [role='option']");
  const scroller = sample ? scrollParent(sample) : null;
  if (!scroller) return findMenuOption(value, true);
  scroller.scrollTop = 0;
  const deadline = Date.now() + 4000;
  for (let step = 0; step < 40 && Date.now() < deadline; step++) {
    const found = findMenuOption(value, true);
    if (found) return found;
    const previous = scroller.scrollTop;
    scroller.scrollTop = previous + Math.max(80, scroller.clientHeight);
    scroller.dispatchEvent(new Event("scroll", { bubbles: true }));
    if (scroller.scrollTop <= previous + 1 && step > 2) break;
    await delay(16);
  }
  return findMenuOption(value, true);
}

function scrollParent(el: HTMLElement) {
  let node: HTMLElement | null = el;
  while (node) {
    const style = getComputedStyle(node);
    if ((style.overflowY === "auto" || style.overflowY === "scroll") && node.scrollHeight > node.clientHeight + 10) return node;
    node = node.parentElement;
  }
  return null;
}

async function waitUntil(check: () => boolean, ms: number) {
  const started = Date.now();
  while (Date.now() - started < ms) {
    try {
      if (check()) return true;
    } catch {
      return false;
    }
    await delay(Date.now() - started < 300 ? 12 : 24);
  }
  return false;
}

export async function setCheckbox(hint: SelectorHint, checked = true): Promise<FillResult> {
  if (stopped) return { field: hint.labelText || "checkbox", ok: false, message: "Stopped" };
  const box = findCheckbox(hint.labelText || "") || (await waitForField(hint, 400).then((m) => (m.element instanceof HTMLInputElement ? m.element : null)).catch(() => null));
  if (!box) {
    return { field: hint.labelText || "checkbox", ok: false, message: "Checkbox not found" };
  }
  scrollField(box);
  await delay(80);
  if (box.checked !== checked) {
    box.click();
  }
  if (box.checked !== checked) {
    box.checked = checked;
    box.dispatchEvent(new Event("input", { bubbles: true }));
    box.dispatchEvent(new Event("change", { bubbles: true }));
  }
  return { field: hint.labelText || "checkbox", ok: box.checked === checked, message: checked ? "Checked" : "Unchecked" };
}

function findBestOption(value: string) {
  const needle = normalize(value);
  if (!needle) return undefined;
  const scoped = Array.from(
    document.querySelectorAll<HTMLElement>(
      "[role='listbox'] [role='option'], [role='option'], .ant-select-item, .MuiMenuItem-root, [class*='MuiMenuItem'], [class*='MenuItem'], [class*='option'], [class*='Option'], [class*='menu'] li, [class*='popover'] li, [class*='Popover'] [class*='item'], li",
    ),
  );
  const fallback = Array.from(document.querySelectorAll<HTMLElement>("div, span, button"));
  const nodes = (scoped.length ? scoped : fallback).filter((el) => {
    if (el.closest("#cs-sidebar") || el.id === "cs-fab") return false;
    return visible(el);
  });
  const exact = nodes
    .filter((el) => {
      const text = normalize(el.textContent || "");
      if (!text || text.length > 80) return false;
      return optionTextMatches(text, needle);
    })
    .sort((a, b) => {
      const at = normalize(a.textContent || "");
      const bt = normalize(b.textContent || "");
      const aExact = at === needle ? 0 : 1;
      const bExact = bt === needle ? 0 : 1;
      if (aExact !== bExact) return aExact - bExact;
      return at.length - bt.length;
    })[0];
  if (exact) return exact;
  return nearestNumericOption(nodes, value);
}

function nearestNumericOption(nodes: HTMLElement[], value: string) {
  const target = Number(String(value).replace(/[^\d.]/g, ""));
  if (!Number.isFinite(target) || target <= 0) return undefined;
  const scored = nodes
    .map((el) => {
      const text = (el.textContent || "").trim().split("\n")[0];
      if (!text || text.length > 24) return null;
      const parsed = parseInch(text);
      if (parsed == null) return null;
      return { el, dist: Math.abs(parsed - target) };
    })
    .filter((item): item is { el: HTMLElement; dist: number } => Boolean(item))
    .sort((a, b) => a.dist - b.dist);
  if (!scored[0]) return undefined;
  const limit = target <= 12 ? 0.5 : 2;
  return scored[0].dist <= limit ? scored[0].el : undefined;
}

function parseInch(text: string) {
  return leadingMeasure(normalize(text));
}

function nearestSelectOption(options: HTMLOptionElement[], value: string) {
  const target = leadingMeasure(normalize(value));
  if (target == null) return undefined;
  const limit = target <= 12 ? 0.5 : 2;
  let best: { option: HTMLOptionElement; dist: number } | undefined;
  for (const option of options) {
    const text = normalize(option.text || option.value);
    if (!text || /^select|choose$/.test(text)) continue;
    const parsed = leadingMeasure(text);
    if (parsed == null) continue;
    const dist = Math.abs(parsed - target);
    if (!best || dist < best.dist) best = { option, dist };
  }
  return best && best.dist <= limit ? best.option : undefined;
}

export function optionTextMatches(optionText: string, value: string) {
  const a = normalize(optionText);
  const b = normalize(value);
  if (!a || !b) return false;
  if (a === b) return true;
  const aRate = parseRate(a);
  const bRate = parseRate(b);
  if (aRate != null && bRate != null) return aRate === bRate;
  const aNum = leadingMeasure(a);
  const bNum = leadingMeasure(b);
  if (aNum != null && bNum != null) return aNum === bNum;
  if (/^\d/.test(a) && /^\d/.test(b)) return false;
  return a.includes(b) || b.includes(a);
}

function leadingMeasure(value: string) {
  const pack = value.match(/^(?:pack of|set of)\s*(\d+(?:\.\d+)?)$/);
  if (pack) return Number(pack[1]);
  const match = value.match(/^(\d+(?:\.\d+)?)(?:\s*(?:%|m|meter|meters|metre|metres|in|inch|inches|n|gm|gms|grams?))?$/);
  return match ? Number(match[1]) : null;
}

function parseRate(value: string) {
  const match = value.match(/^(\d+(?:\.\d+)?)\s*%?$/);
  return match ? Number(match[1]) : null;
}

function normalize(value: string) {
  return value.toLowerCase().replace(/\s+/g, " ").trim();
}

export function verifyFieldValue(el: HTMLElement, expected: string) {
  const current = "value" in el ? String((el as HTMLInputElement).value) : el.textContent || "";
  const needle = expected.toLowerCase().slice(0, Math.min(12, expected.length));
  if (!needle) return false;
  if (current.toLowerCase().includes(needle)) return true;
  const host = (el.closest("[class*='select'], [class*='Select'], [class*='dropdown']") as HTMLElement | null)?.innerText || "";
  return host.toLowerCase().includes(needle);
}

export async function fillByHints(
  steps: { hint: SelectorHint; value: string; type?: "text" | "textarea" | "select" | "checkbox"; exact?: boolean; element?: HTMLElement }[],
  onProgress?: (message: string, done?: number, total?: number) => void,
) {
  const results: FillResult[] = [];
  onProgress?.("Filling marketplace form...", 0, steps.length);
  for (const step of steps) {
    if (stopped) {
      results.push({ field: step.hint.labelText || "field", ok: false, message: "Stopped by user" });
      break;
    }
    const label = step.hint.labelText || step.hint.name || "field";
    const done = results.filter((item) => item.ok).length;
    onProgress?.(`${done} done · ${steps.length - results.length} left · ${label}`, done, steps.length);
    try {
      let result: FillResult;
      if (step.type === "checkbox") {
        result = await setCheckbox(step.hint, step.value !== "false");
      } else if (step.type === "select") {
        result = await selectNativeDropdown(step.hint, step.value, step.element, Boolean(step.exact));
      } else if (step.type === "textarea") {
        result = await fillTextArea(step.hint, step.value, step.element);
      } else {
        result = await fillTextInput(step.hint, step.value, step.element);
      }
      results.push(result);
      if (!result.ok) closeOpenMenus();
      else await delay(60);
      onProgress?.(
        result.ok ? `Filling ${label}...` : result.message,
        results.filter((item) => item.ok).length,
        steps.length,
      );
    } catch (error) {
      results.push({ field: label, ok: false, message: error instanceof Error ? error.message : "Failed" });
    }
  }
  const failed = steps.filter((step, index) => results[index] && !results[index].ok && results[index].message !== "Stopped by user" && results[index].message !== "Stopped");
  for (const step of failed) {
    if (stopped) break;
    const label = step.hint.labelText || step.hint.name || "field";
    try {
      const result = step.type === "checkbox"
        ? await setCheckbox(step.hint, step.value !== "false")
        : step.type === "select"
          ? await selectNativeDropdown(step.hint, step.value, step.element, Boolean(step.exact))
          : step.type === "textarea"
            ? await fillTextArea(step.hint, step.value, step.element)
            : await fillTextInput(step.hint, step.value, step.element);
      const at = results.findIndex((item) => item.field === label && !item.ok);
      if (at >= 0) results[at] = result.ok ? { ...result, message: "Filled" } : result;
    } catch (error) {
      // leave the first failure
    }
  }
  const filled = results.filter((item) => item.ok).length;
  closeOpenMenus();
  onProgress?.(`Filled ${filled} of ${results.length} fields. Review, then submit yourself.`, filled, results.length);
  return results;
}

function delay(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}

export { findField, waitForField };
