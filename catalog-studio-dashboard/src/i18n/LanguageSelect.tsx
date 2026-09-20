import { LOCALES } from "./catalog";
import { useI18n } from "./LanguageProvider";

export function LanguageSelect({ compact = false }: { compact?: boolean }) {
  const { locale, setLocale, t } = useI18n();
  return (
    <label className={`inline-flex items-center gap-2 ${compact ? "" : "w-full"}`}>
      {!compact && <span className="text-sm font-medium text-slate-700">{t("lang.label")}</span>}
      <select
        value={locale}
        onChange={(event) => setLocale(event.target.value)}
        aria-label={t("lang.label")}
        className={`rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm text-slate-700 outline-none focus:border-teal-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 ${
          compact ? "max-w-[10.5rem]" : "mt-1 w-full"
        }`}
      >
        {LOCALES.map((item) => (
          <option key={item.code} value={item.code}>
            {item.native}
          </option>
        ))}
      </select>
    </label>
  );
}
