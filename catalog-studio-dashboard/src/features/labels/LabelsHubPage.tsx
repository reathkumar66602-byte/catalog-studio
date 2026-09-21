import { Link } from "react-router-dom";
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
      logo: <FlipkartLogo className="h-12 w-12" />,
    },
    {
      to: "/tools/labels/meesho",
      label: t("land.meCrop"),
      text: t("labels.meText"),
      logo: <MeeshoLogo className="h-12 w-12" />,
    },
    {
      to: "/tools/labels/merge",
      label: t("land.merge"),
      text: t("labels.mergeText"),
      logo: <MergePdfLogo className="h-12 w-12" />,
    },
  ];
  return (
    <div className="mx-auto max-w-5xl space-y-8">
      <div>
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-teal-700">{t("labels.hubKicker")}</p>
        <h1 className="mt-2 text-3xl font-semibold tracking-tight text-slate-900">{t("labels.hubTitle")}</h1>
        <p className="mt-2 max-w-2xl text-sm text-slate-500 md:text-base">
          {t("labels.hubSub")}
          {site.client ? ` — ${site.client.storeName}` : ""}
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        {tools.map((tool) => (
          <Link
            key={tool.to}
            to={tool.to}
            className="flex gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-teal-600 hover:shadow-md"
          >
            {tool.logo}
            <div>
              <h2 className="font-semibold text-slate-900">{tool.label}</h2>
              <p className="mt-1 text-sm text-slate-500">{tool.text}</p>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
