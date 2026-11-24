import { Package } from "lucide-react";
import { useI18n } from "../../i18n/LanguageProvider";

/** E-commerce inventory workspace — placeholder until the module ships. */
export function InventoryPage() {
  const { t } = useI18n();

  return (
    <div className="mx-auto flex max-w-2xl flex-col items-center justify-center px-4 py-16 text-center">
      <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-teal-50 text-teal-800 dark:bg-teal-950/40 dark:text-teal-200">
        <Package size={28} />
      </div>
      <h1 className="text-2xl font-semibold">{t("nav.inventory")}</h1>
      <p className="mt-3 max-w-md text-sm text-slate-600 dark:text-slate-300">
        This feature is launching soon. You will manage marketplace-ready e-commerce stock here.
      </p>
      <p className="mt-6 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-950 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-100">
        Launching soon
      </p>
      <p className="mt-4 text-xs text-slate-500">
        For wholesale stock today, use <span className="font-semibold">Supplier inventory</span>.
      </p>
    </div>
  );
}
