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
  if (lower === "regular") add("Regular Fit");
  if (lower === "regular fit") add("Regular");
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
  document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
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

export async function selectNativeDropdown(hint: SelectorHint, value: string, element?: HTMLElement): Promise<FillResult> {
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
    return selectCustomDropdown(hint, value, match.element);
  }
  const option = Array.from(match.element.options).find((o) => optionTextMatches(o.text || o.value, value));
  if (!option) {
    return { field: match.label, ok: false, message: "Dropdown value is unavailable" };
  }
  match.element.value = option.value;
  match.element.dispatchEvent(new Event("change", { bubbles: true }));
  return { field: match.label, ok: true, message: "Selected" };
}

export async function selectCustomDropdown(hint: SelectorHint, value: string, element?: HTMLElement): Promise<FillResult> {
  if (stopped) return { field: hint.labelText || "dropdown", ok: false, message: "Stopped" };
  let match: { element: HTMLElement; label: string };
  try {
    match = element ? { element, label: hint.labelText || "dropdown" } : await waitForField(hint, 1500);
  } catch {
    return { field: hint.labelText || "dropdown", ok: false, message: "Field not on this form" };
  }
  scrollField(match.element);
  await delay(80);
  closeOpenMenus();
  await delay(40);
  const opener =
    (match.element.closest("[class*='select'], [class*='Select'], [class*='dropdown'], [aria-haspopup='listbox']") as HTMLElement | null)
    || match.element;
  for (const candidate of dropdownCandidates(value)) {
    realClick(opener);
    await delay(350);
    const search =
      document.querySelector<HTMLInputElement>("[role='listbox'] input, .ant-select-dropdown input, input[type='search']")
      || opener.querySelector("input")
      || (match.element instanceof HTMLInputElement ? match.element : null);
    if (search && !(search instanceof HTMLInputElement && search.type === "number" && !isNumericString(candidate))) {
      triggerReactInputEvents(search, candidate);
      await delay(220);
    }
    const option = findBestOption(candidate) || (await delay(280), findBestOption(candidate));
    if (option) {
      realClick(option);
      await delay(180);
      return { field: match.label, ok: true, message: "Selected" };
    }
    closeOpenMenus();
    await delay(80);
  }
  return { field: match.label, ok: false, message: "Dropdown value is unavailable" };
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
  return scored[0] && scored[0].dist <= 2 ? scored[0].el : undefined;
}

function parseInch(text: string) {
  const match = text.replace(/,/g, "").match(/^(\d+(?:\.\d+)?)(\s*(in|inch|inches)?)?$/i);
  return match ? Number(match[1]) : null;
}

function optionTextMatches(optionText: string, value: string) {
  const a = normalize(optionText);
  const b = normalize(value);
  if (!a || !b) return false;
  if (a === b) return true;
  const aRate = parseRate(a);
  const bRate = parseRate(b);
  if (aRate != null && bRate != null) return aRate === bRate;
  return a.includes(b) || b.includes(a);
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
  steps: { hint: SelectorHint; value: string; type?: "text" | "textarea" | "select" | "checkbox"; element?: HTMLElement }[],
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
        result = await selectNativeDropdown(step.hint, step.value, step.element);
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
  const filled = results.filter((item) => item.ok).length;
  closeOpenMenus();
  onProgress?.(`Filled ${filled} of ${results.length} fields. Review, then submit yourself.`, filled, results.length);
  return results;
}

function delay(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}

export { findField, waitForField };
