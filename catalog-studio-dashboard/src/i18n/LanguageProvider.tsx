import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api } from "../api/client";
import { useAuth } from "../store/auth";
import {
  detectLocale,
  localeMeta,
  LOCALE_STORAGE_KEY,
  normalizeLocale,
  translate,
  type LocaleCode,
} from "./catalog";

type I18nState = {
  locale: LocaleCode;
  dir: "ltr" | "rtl";
  setLocale: (next: string) => void;
  t: (key: string, vars?: Record<string, string | number>) => string;
};

const I18nContext = createContext<I18nState | null>(null);

function applyDocumentLocale(locale: LocaleCode) {
  const meta = localeMeta(locale);
  document.documentElement.lang = locale;
  document.documentElement.dir = meta.dir;
  localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  window.postMessage({ type: "CS_LOCALE", locale }, window.location.origin);
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [locale, setLocaleState] = useState<LocaleCode>(() => detectLocale());

  const persistAccount = useCallback((next: LocaleCode) => {
    if (!localStorage.getItem("cs_access")) return;
    void api.put("/me", { preferredLocale: next }).catch(() => undefined);
  }, []);

  const setLocale = useCallback(
    (next: string) => {
      const code = normalizeLocale(next);
      setLocaleState(code);
      applyDocumentLocale(code);
      persistAccount(code);
    },
    [persistAccount],
  );

  useEffect(() => {
    applyDocumentLocale(locale);
  }, [locale]);

  useEffect(() => {
    const fromAccount = user?.preferredLocale;
    if (!fromAccount) return;
    const stored = localStorage.getItem(LOCALE_STORAGE_KEY);
    if (stored) return;
    const code = normalizeLocale(fromAccount);
    setLocaleState(code);
    applyDocumentLocale(code);
  }, [user?.preferredLocale]);

  const value = useMemo<I18nState>(
    () => ({
      locale,
      dir: localeMeta(locale).dir,
      setLocale,
      t: (key, vars) => translate(locale, key, vars),
    }),
    [locale, setLocale],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const ctx = useContext(I18nContext);
  if (!ctx) {
    throw new Error("LanguageProvider missing");
  }
  return ctx;
}
