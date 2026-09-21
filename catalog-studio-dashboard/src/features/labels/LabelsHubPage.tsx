import { Link } from "react-router-dom";
import { ChevronRight } from "lucide-react";
import { FlipkartLogo, MeeshoLogo, MergePdfLogo } from "./logos";
import { useSite } from "../site/useSite";
import { useI18n } from "../../i18n/LanguageProvider";

export function LabelsHubPage() {
  const site = useSite();
  const { t } = useI18n();
  const tools = [
    {
      to: "/tools/labels/flipkart",
      label: t("land.fkCrop"),
      text: t("labels.fkText"),
      hint: t("land.fkHint"),
      logo: <FlipkartLogo className="h-12 w-12 sm:h-14 sm:w-14" />,
      rail: "bg-[#ffe500]",
    },
    {
      to: "/tools/labels/meesho",
      label: t("land.meCrop"),
      text: t("labels.meText"),
      hint: t("land.meHint"),
      logo: <MeeshoLogo className="h-12 w-12 sm:h-14 sm:w-14" />,
      rail: "bg-[#f43397]",
    },
    {
      to: "/tools/labels/merge",
      label: t("land.merge"),
      text: t("labels.mergeText"),
      hint: t("land.mergeHint"),
      logo: <MergePdfLogo className="h-12 w-12 sm:h-14 sm:w-14" />,
      rail: "bg-teal-700",
    },
  ];

  return (
    <div className="mx-auto max-w-4xl">
      <section className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-teal-800 via-teal-700 to-slate-900 px-6 py-8 text-white shadow-sm md:px-8">
        <p className="text-xs font-semibold uppercase tracking-[0.22em] text-teal-100/80">{t("labels.hubKicker")}</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight">{t("labels.hubTitle")}</h1>
        <p className="mt-2 max-w-2xl text-sm text-teal-50/90 md:text-base">
          {t("labels.hubSub")}
          {site.client ? ` — ${site.client.storeName}` : ""}
        </p>
      </section>

      <ol className="mt-6 overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900">
        {tools.map((tool, index) => (
          <li key={tool.to} className={index > 0 ? "border-t border-slate-200 dark:border-slate-800" : undefined}>
            <Link
              to={tool.to}
              className="group flex items-stretch transition hover:bg-slate-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-teal-700 dark:hover:bg-slate-800/70"
            >
              <span className={`w-1.5 shrink-0 ${tool.rail}`} aria-hidden="true" />
              <div className="flex min-w-0 flex-1 items-center gap-4 px-4 py-5 sm:gap-5 sm:px-6">
                {tool.logo}
                <div className="min-w-0 flex-1">
                  <h2 className="font-semibold text-slate-900 dark:text-slate-50">{tool.label}</h2>
                  <p className="mt-1 text-sm leading-relaxed text-slate-500 dark:text-slate-400">{tool.text}</p>
                  <p className="mt-2 text-xs font-medium text-teal-800 dark:text-teal-300">{tool.hint}</p>
                </div>
                <span className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-teal-700 text-white shadow-sm transition group-hover:bg-teal-800">
                  <ChevronRight size={18} aria-hidden="true" />
                </span>
              </div>
            </Link>
          </li>
        ))}
      </ol>
    </div>
  );
}
