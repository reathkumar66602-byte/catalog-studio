import { useQuery } from "@tanstack/react-query";
import { Cloud, Database, RefreshCw, TriangleAlert } from "lucide-react";
import { api, apiErrorMessage } from "../../api/client";
import { useI18n } from "../../i18n/LanguageProvider";

type CloudCostDashboard = {
  enabled: boolean;
  status: string;
  message: string;
  currencyCode: string;
  billingAccountId: string;
  generatedAt: string;
  summary: {
    monthToDate: number;
    previousMonth: number;
    percentChange: number;
    annualBudget: number;
    periodSpendAgainstAnnual: number;
    remainingAgainstAnnual: number;
    forecastMonth: number;
  };
  projects: Array<{
    projectId: string;
    name: string;
    note?: string;
    cost: number;
    netCost: number;
  }>;
  services: Array<{
    service: string;
    cost: number;
    credits: number;
    netCost: number;
  }>;
  daily: Array<{ date: string; cost: number }>;
  budgets: Array<{
    id: string;
    displayName: string;
    periodLabel: string;
    amount: number;
    thresholds: number[];
    emailAlertsConfigured: boolean;
    pubsubConfigured: boolean;
    pubsubTopic?: string;
    projectIds: string[];
    creditTreatment: string;
  }>;
  resources: Array<{
    projectId: string;
    kind: string;
    name: string;
    detail: string;
  }>;
  exportStatus: {
    bigQueryConfigured: boolean;
    projectId: string;
    datasetId: string;
    tableId: string;
    tableReady: boolean;
    hint: string;
  };
};

function inr(value: number | undefined | null, currency = "INR") {
  const n = Number(value || 0);
  return n.toLocaleString("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  });
}

function statusTone(status: string) {
  if (status === "OK") return "border-emerald-200 bg-emerald-50 text-emerald-800";
  if (status === "WAITING_FOR_EXPORT" || status === "PARTIAL") return "border-amber-200 bg-amber-50 text-amber-900";
  return "border-slate-200 bg-slate-50 text-slate-700";
}

export function CloudCostAdminPage() {
  const { t } = useI18n();
  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ["admin-cloud-cost"],
    queryFn: async () => (await api.get("/admin/cloud-cost")).data.data as CloudCostDashboard,
    refetchInterval: 5 * 60 * 1000,
  });

  if (isLoading) return <p>{t("admin.cloudCost.loading")}</p>;
  if (error || !data) {
    return <p className="text-red-600">{apiErrorMessage(error, t("admin.cloudCost.loadFail"))}</p>;
  }

  const maxDaily = Math.max(...data.daily.map((d) => d.cost), 1);

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">{t("admin.cloudCost.title")}</h1>
          <p className="mt-1 max-w-3xl text-slate-500">{t("admin.cloudCost.subtitle")}</p>
          <p className="mt-1 text-xs text-slate-400">
            Account {data.billingAccountId} · Updated {new Date(data.generatedAt).toLocaleString("en-IN")}
          </p>
        </div>
        <button
          type="button"
          onClick={() => refetch()}
          className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          <RefreshCw className={`h-4 w-4 ${isFetching ? "animate-spin" : ""}`} />
          Refresh
        </button>
      </div>

      <div className={`rounded-2xl border px-4 py-3 text-sm ${statusTone(data.status)}`}>
        <div className="font-semibold">{data.status.replaceAll("_", " ")}</div>
        <p className="mt-1 opacity-90">{data.message}</p>
        {!data.exportStatus.tableReady && (
          <p className="mt-2 flex items-start gap-2 text-xs opacity-90">
            <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            <span>
              {data.exportStatus.hint}. Dataset: {data.exportStatus.projectId}.{data.exportStatus.datasetId}
              {data.exportStatus.tableId ? ` / ${data.exportStatus.tableId}` : ""}
            </span>
          </p>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Month to date" value={inr(data.summary.monthToDate, data.currencyCode)} hint={`${data.summary.percentChange >= 0 ? "+" : ""}${data.summary.percentChange}% vs last month`} />
        <StatCard label="Forecast (month)" value={inr(data.summary.forecastMonth, data.currencyCode)} hint="Extrapolated from days elapsed" />
        <StatCard label="Against ₹30k plan" value={inr(data.summary.periodSpendAgainstAnnual, data.currencyCode)} hint={`of ${inr(data.summary.annualBudget, data.currencyCode)}`} />
        <StatCard label="Remaining" value={inr(data.summary.remainingAgainstAnnual, data.currencyCode)} hint="Annual budget minus tracked spend" accent />
      </div>

      <section className="grid gap-4 lg:grid-cols-2">
        {data.projects.map((project) => (
          <article key={project.projectId} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-slate-900">{project.name}</h2>
                <p className="text-xs text-slate-400">{project.projectId}</p>
              </div>
              <Cloud className="h-5 w-5 text-teal-700" />
            </div>
            <p className="mt-4 text-3xl font-semibold tabular-nums text-slate-900">{inr(project.cost, data.currencyCode)}</p>
            <p className="text-sm text-slate-500">Net after credits: {inr(project.netCost, data.currencyCode)}</p>
            {project.note ? <p className="mt-3 text-sm text-slate-600">{project.note}</p> : null}
          </article>
        ))}
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Usage by service</h2>
        {data.services.length === 0 ? (
          <p className="mt-3 text-sm text-slate-500">No service rows yet — waiting for BigQuery export data.</p>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="border-b border-slate-200 text-slate-500">
                <tr>
                  <th className="py-2 pr-4 font-medium">Service</th>
                  <th className="py-2 pr-4 font-medium">Usage</th>
                  <th className="py-2 pr-4 font-medium">Credits</th>
                  <th className="py-2 font-medium">Net</th>
                </tr>
              </thead>
              <tbody>
                {data.services.map((row) => (
                  <tr key={row.service} className="border-b border-slate-100">
                    <td className="py-2.5 pr-4 font-medium text-slate-800">{row.service}</td>
                    <td className="py-2.5 pr-4 tabular-nums">{inr(row.cost, data.currencyCode)}</td>
                    <td className="py-2.5 pr-4 tabular-nums text-emerald-700">{inr(row.credits, data.currencyCode)}</td>
                    <td className="py-2.5 tabular-nums">{inr(row.netCost, data.currencyCode)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Daily spend (this month)</h2>
        {data.daily.length === 0 ? (
          <p className="mt-3 text-sm text-slate-500">No daily points yet.</p>
        ) : (
          <div className="mt-4 flex h-40 items-end gap-1">
            {data.daily.map((day) => (
              <div key={day.date} className="group relative flex min-w-0 flex-1 flex-col items-center justify-end">
                <div
                  className="w-full rounded-t bg-teal-600/80 transition group-hover:bg-teal-700"
                  style={{ height: `${Math.max(4, (day.cost / maxDaily) * 100)}%` }}
                  title={`${day.date}: ${inr(day.cost, data.currencyCode)}`}
                />
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Budgets and alerts</h2>
        {data.budgets.length === 0 ? (
          <p className="mt-3 text-sm text-slate-500">Could not load budgets (ADC / permissions).</p>
        ) : (
          <div className="mt-4 grid gap-3">
            {data.budgets.map((budget) => (
              <div key={budget.id} className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <div className="font-semibold text-slate-900">{budget.displayName}</div>
                    <div className="text-xs text-slate-500">{budget.periodLabel} · {inr(budget.amount, data.currencyCode)}</div>
                  </div>
                  <div className="text-xs text-slate-600">
                    Alerts at {(budget.thresholds || []).map((t) => `${Math.round(t * 100)}%`).join(", ") || "—"}
                  </div>
                </div>
                <div className="mt-2 flex flex-wrap gap-2 text-xs">
                  <Badge ok={budget.emailAlertsConfigured} okText="Email (IAM default)" badText="Email disabled" />
                  <Badge ok={budget.pubsubConfigured} okText="Pub/Sub stop hook" badText="No Pub/Sub" />
                </div>
                {budget.pubsubTopic ? <p className="mt-2 truncate text-xs text-slate-400">{budget.pubsubTopic}</p> : null}
              </div>
            ))}
          </div>
        )}
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex items-center gap-2">
          <Database className="h-5 w-5 text-teal-700" />
          <h2 className="text-lg font-semibold text-slate-900">Tracked resources</h2>
        </div>
        <div className="mt-4 overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-slate-500">
              <tr>
                <th className="py-2 pr-4 font-medium">Project</th>
                <th className="py-2 pr-4 font-medium">Kind</th>
                <th className="py-2 pr-4 font-medium">Name</th>
                <th className="py-2 font-medium">Detail</th>
              </tr>
            </thead>
            <tbody>
              {data.resources.map((row) => (
                <tr key={`${row.projectId}-${row.kind}-${row.name}`} className="border-b border-slate-100">
                  <td className="py-2.5 pr-4 text-slate-600">{row.projectId}</td>
                  <td className="py-2.5 pr-4 font-medium text-slate-800">{row.kind}</td>
                  <td className="py-2.5 pr-4">{row.name}</td>
                  <td className="py-2.5 text-slate-600">{row.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}

function StatCard({
  label,
  value,
  hint,
  accent,
}: {
  label: string;
  value: string;
  hint: string;
  accent?: boolean;
}) {
  return (
    <div className={`rounded-2xl border p-4 shadow-sm ${accent ? "border-teal-200 bg-teal-50" : "border-slate-200 bg-white"}`}>
      <div className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</div>
      <div className="mt-2 text-2xl font-semibold tabular-nums text-slate-900">{value}</div>
      <div className="mt-1 text-xs text-slate-500">{hint}</div>
    </div>
  );
}

function Badge({ ok, okText, badText }: { ok: boolean; okText: string; badText: string }) {
  return (
    <span
      className={`rounded-full px-2.5 py-1 font-medium ${
        ok ? "bg-emerald-100 text-emerald-800" : "bg-amber-100 text-amber-900"
      }`}
    >
      {ok ? okText : badText}
    </span>
  );
}
