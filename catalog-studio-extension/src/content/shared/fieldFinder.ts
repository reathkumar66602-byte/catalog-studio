export type FieldMatch = {
  element: HTMLElement;
  strategy: string;
  label: string;
};

export type SelectorHint = {
  labelText?: string;
  name?: string;
  placeholder?: string;
  ariaLabel?: string;
  dataAttr?: string;
  css?: string;
};

export function visible(el: Element | null | undefined) {
  if (!el || el.nodeType !== Node.ELEMENT_NODE) return false;
  try {
    const style = window.getComputedStyle(el);
    if (!style || style.display === "none" || style.visibility === "hidden") {
      return false;
    }
    return true;
  } catch {
    return false;
  }
}

export function readFieldValue(hint: SelectorHint) {
  const match = findField(hint);
  if (!match) return "";
  const el = match.element;
  if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement) {
    return el.value.trim();
  }
  return (el.textContent || "").trim();
}

export function findCheckbox(labelText: string): HTMLInputElement | null {
  const needle = normalize(labelText);
  if (!needle) return null;
  const labeled = Array.from(document.querySelectorAll<HTMLElement>("label, p, span, div, li"));
  for (const el of labeled) {
    if (el.closest("#cs-sidebar")) continue;
    const text = normalize((el.innerText || el.textContent || "").split("\n")[0] || "");
    if (!text || text.length > 80) continue;
    if (!(text === needle || text.includes(needle) || needle.includes(text))) continue;
    const box =
      el.querySelector<HTMLInputElement>("input[type='checkbox']") ||
      (el.previousElementSibling instanceof HTMLInputElement && el.previousElementSibling.type === "checkbox"
        ? el.previousElementSibling
        : null) ||
      el.parentElement?.querySelector<HTMLInputElement>("input[type='checkbox']") ||
      null;
    if (box && !box.closest("#cs-sidebar")) return box;
  }
  for (const box of Array.from(document.querySelectorAll<HTMLInputElement>("input[type='checkbox']"))) {
    if (box.closest("#cs-sidebar")) continue;
    const hostText = normalize((box.closest("label")?.innerText || box.parentElement?.innerText || box.nextSibling?.textContent || "") as string);
    if (!hostText) continue;
    if (hostText.includes(needle) || needle.includes(hostText)) return box;
  }
  return null;
}

export function findField(hint: SelectorHint): FieldMatch | null {
  try {
    if (hint.labelText) {
      const nearby = findFieldByNearbyLabel(hint.labelText);
      if (nearby) return nearby;
    }
    const inputs = Array.from(document.querySelectorAll<HTMLElement>("input, textarea, select, [role='combobox'], [contenteditable='true']"));
    for (const el of inputs) {
      if (el.closest("#cs-sidebar")) continue;
      if (!visible(el)) continue;
      const label = associatedLabel(el);
      if (hint.labelText && label && normalize(label).includes(normalize(hint.labelText))) {
        return { element: el, strategy: "LABEL_BASED", label };
      }
      if (hint.name && el.getAttribute("name") && normalize(el.getAttribute("name")!) === normalize(hint.name)) {
        return { element: el, strategy: "NAME", label: hint.name };
      }
      if (hint.placeholder && "placeholder" in el && normalize(String((el as HTMLInputElement).placeholder)).includes(normalize(hint.placeholder))) {
        return { element: el, strategy: "PLACEHOLDER", label: hint.placeholder };
      }
      if (hint.ariaLabel && el.getAttribute("aria-label") && normalize(el.getAttribute("aria-label")!).includes(normalize(hint.ariaLabel))) {
        return { element: el, strategy: "ACCESSIBILITY_LABEL", label: hint.ariaLabel };
      }
      if (hint.dataAttr) {
        const [attr, value] = hint.dataAttr.split("=");
        if (value && el.getAttribute(attr) === value.replaceAll('"', "")) {
          return { element: el, strategy: "DATA_ATTRIBUTE", label: hint.dataAttr };
        }
      }
    }
    if (hint.css) {
      const el = document.querySelector<HTMLElement>(hint.css);
      if (el && visible(el)) {
        return { element: el, strategy: "CSS_SELECTOR", label: hint.css };
      }
    }
  } catch {
    return null;
  }
  return null;
}

export async function waitForField(hint: SelectorHint, timeoutMs = 2500): Promise<FieldMatch> {
  const started = Date.now();
  return new Promise((resolve, reject) => {
    let settled = false;
    let observer: MutationObserver | null = null;
    const succeed = (match: FieldMatch) => {
      if (settled) return;
      settled = true;
      observer?.disconnect();
      resolve(match);
    };
    const fail = () => {
      if (settled) return;
      settled = true;
      observer?.disconnect();
      reject(new Error(`Field not found: ${hint.labelText || hint.name || hint.css}`));
    };
    const existing = findField(hint);
    if (existing) {
      succeed(existing);
      return;
    }
    if (!document.body) {
      fail();
      return;
    }
    observer = new MutationObserver(() => {
      try {
        const match = findField(hint);
        if (match) succeed(match);
        else if (Date.now() - started > timeoutMs) fail();
      } catch {
        fail();
      }
    });
    observer.observe(document.body, { childList: true, subtree: true, attributes: true });
    setTimeout(fail, timeoutMs);
  });
}

function findFieldByNearbyLabel(labelText: string): FieldMatch | null {
  const needle = normalize(labelText);
  if (!needle) return null;
  const nodes = Array.from(document.querySelectorAll<HTMLElement>("label, p, span, div, dt, h3, h4, h5"));
  const labels = nodes
    .filter((el) => {
      if (el.closest("#cs-sidebar") || el.closest("table")) return false;
      const text = normalize((el.innerText || el.textContent || "").split("\n")[0] || "");
      if (!text || text.length > 72 || isMeasureHeading(text)) return false;
      const exact = text === needle;
      const starts = text.startsWith(needle);
      const contains = text.includes(needle) && text.length <= needle.length + 24;
      return exact || starts || contains;
    })
    .sort((a, b) => {
      const at = normalize((a.innerText || "").split("\n")[0] || "");
      const bt = normalize((b.innerText || "").split("\n")[0] || "");
      const aExact = at === needle ? 0 : at.startsWith(needle) ? 1 : 2;
      const bExact = bt === needle ? 0 : bt.startsWith(needle) ? 1 : 2;
      if (aExact !== bExact) return aExact - bExact;
      return at.length - bt.length;
    });
  for (const labelEl of labels) {
    const scope = labelEl.parentElement;
    const combo = scope?.querySelector<HTMLElement>("select, [role='combobox']");
    if (combo && !combo.closest("#cs-sidebar") && !combo.closest("table")) {
      return { element: combo, strategy: "NEARBY_LABEL", label: labelText };
    }
    const input = scope?.querySelector<HTMLElement>("input, textarea, [contenteditable='true']");
    if (input && !input.closest("#cs-sidebar") && !input.closest("table")) {
      return { element: input, strategy: "NEARBY_LABEL", label: labelText };
    }
    const next = labelEl.nextElementSibling?.querySelector?.("input, textarea, select, [role='combobox'], [contenteditable='true']")
      || (labelEl.nextElementSibling as HTMLElement | null);
    if (next && isFieldElement(next) && !next.closest("#cs-sidebar")) {
      return { element: next, strategy: "NEARBY_LABEL", label: labelText };
    }
  }
  return null;
}

function isMeasureHeading(text: string) {
  if (/^(shoulder|waist|hip|bust|chest) size/.test(text)) return true;
  if (/^size length|length size/.test(text)) return true;
  return /\binch|\bin\b/.test(text) && /shoulder|waist|hip|bust|chest|length/.test(text);
}

function isFieldElement(el: Element | null | undefined): el is HTMLElement {
  try {
    return Boolean(el && typeof el.matches === "function" && el.matches("input, textarea, select, [role='combobox'], [contenteditable='true']"));
  } catch {
    return false;
  }
}

function associatedLabel(el: HTMLElement) {
  const id = el.getAttribute("id");
  if (id) {
    const byFor = document.querySelector(`label[for="${cssEscape(id)}"]`);
    if (byFor?.textContent) return byFor.textContent;
  }
  const parent = el.closest("label");
  if (parent?.textContent) return parent.textContent.trim().slice(0, 80);
  let node: HTMLElement | null = el;
  for (let i = 0; i < 5 && node; i += 1) {
    const prev = node.previousElementSibling as HTMLElement | null;
    const prevText = (prev?.innerText || "").trim().split("\n")[0] || "";
    if (prevText && prevText.length < 48) return prevText;
    node = node.parentElement;
  }
  return el.getAttribute("aria-label") || "";
}

function normalize(value: unknown) {
  return String(value || "").toLowerCase().replace(/\s+/g, " ").trim();
}

function cssEscape(value: string) {
  if (typeof CSS !== "undefined" && typeof CSS.escape === "function") {
    return CSS.escape(value);
  }
  return value.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}
