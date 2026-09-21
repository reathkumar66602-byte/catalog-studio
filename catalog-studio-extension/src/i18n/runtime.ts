import { LOCALES, normalizeLocale, translate, type LocaleCode } from "./catalog";

let locale: LocaleCode = "en";

export function t(key: string, vars?: Record<string, string | number>) {
  return translate(locale, key, vars);
}

export function currentLocale() {
  return locale;
}

export async function loadLocale() {
  try {
    const data = await chrome.storage.local.get(["csLocale"]);
    locale = normalizeLocale(String(data.csLocale || ""));
  } catch {
    locale = "en";
  }
  return locale;
}

export function setLocale(next: string) {
  locale = normalizeLocale(next);
  void chrome.storage.local.set({ csLocale: locale });
}

export function localeOptionsHtml() {
  return LOCALES.map(
    (item) => `<option value="${item.code}"${item.code === locale ? " selected" : ""}>${item.native}</option>`,
  ).join("");
}

export function applyStaticI18n(root: ParentNode = document) {
  root.querySelectorAll<HTMLElement>("[data-i18n]").forEach((el) => {
    const key = el.getAttribute("data-i18n");
    if (key) el.textContent = t(key);
  });
  root.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>("[data-i18n-placeholder]").forEach((el) => {
    const key = el.getAttribute("data-i18n-placeholder");
    if (key) el.placeholder = t(key);
  });
}

export { LOCALES };
