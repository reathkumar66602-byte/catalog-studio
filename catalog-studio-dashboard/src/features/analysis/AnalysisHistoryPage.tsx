import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import { useI18n } from "../../i18n/LanguageProvider";

type AnalysisRow = {
  id: string;
  productId?: string;
  productName?: string;
  productImage?: string;
  provider: string;
  model: string;
  status: string;
  confidence?: number;
  createdAt: string;
};

type AnalysisPage = {
  content: AnalysisRow[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export function AnalysisHistoryPage() {
  const { t } = useI18n();
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["analysis", page],
    queryFn: async () => (await api.get("/analysis", { params: { page, size: 20 } })).data.data as AnalysisPage,
  });
  const rows = data?.content || [];
  const totalPages = data?.totalPages || 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">{t("analysisHistory.title")}</h1>
          <p className="mt-1 text-sm text-slate-500">
            {t("analysisHistory.subtitle")}
            {data ? ` · ${t("analysisHistory.total", { total: data.totalElements })}` : ""}
          </p>
        </div>
        <Link
          to="/products/analyze"
          className="rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-800"
        >
          {t("nav.productAnalysis")}
        </Link>
      </div>
      <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="p-3">{t("inventory.product")}</th>
              <th>Provider</th>
              <th>Model</th>
              <th>Status</th>
              <th>Confidence</th>
              <th>Created</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={7}>
                  {t("analysisHistory.loading")}
                </td>
              </tr>
            )}
            {isError && (
              <tr>
                <td className="p-6" colSpan={7}>
                  <p className="text-rose-700">{apiErrorMessage(error, t("analysisHistory.loadFail"))}</p>
                  <button type="button" className="mt-2 text-sm text-teal-700" onClick={() => refetch()}>
                    {t("common.retry")}
                  </button>
                </td>
              </tr>
            )}
            {!isLoading && !isError && rows.length === 0 && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={7}>
                  {t("analysisHistory.empty")}
                </td>
              </tr>
            )}
            {rows.map((row) => (
              <tr key={row.id} className="border-t">
                <td className="p-3">
                  <div className="flex items-center gap-3">
                    {row.productImage ? (
                      <img src={row.productImage} alt="" className="h-10 w-10 rounded-lg object-cover" />
                    ) : null}
                    <span>{row.productName || t("analysisHistory.draft")}</span>
                  </div>
                </td>
                <td>{row.provider}</td>
                <td>{row.model}</td>
                <td>{row.status}</td>
                <td>{row.confidence != null ? Math.round(Number(row.confidence) * 100) + "%" : "—"}</td>
                <td>{row.createdAt ? new Date(row.createdAt).toLocaleString("en-IN") : "—"}</td>
                <td>
                  {row.productId && (
                    <Link className="text-teal-700" to={`/products/${row.productId}/review`}>
                      {t("analysisHistory.view")}
                    </Link>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {totalPages > 1 && (
        <div className="flex items-center gap-3 text-sm">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((current) => Math.max(0, current - 1))}
            className="rounded-lg border px-3 py-1.5 disabled:opacity-50"
          >
            {t("common.previous")}
          </button>
          <span>{t("common.pageOf", { page: page + 1, total: totalPages })}</span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((current) => current + 1)}
            className="rounded-lg border px-3 py-1.5 disabled:opacity-50"
          >
            {t("common.next")}
          </button>
        </div>
      )}
    </div>
  );
}
