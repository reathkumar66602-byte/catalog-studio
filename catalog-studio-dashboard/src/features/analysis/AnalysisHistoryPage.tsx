import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";

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
  const [page, setPage] = useState(0);
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["analysis", page],
    queryFn: async () => (await api.get("/analysis", { params: { page, size: 20 } })).data.data as AnalysisPage,
  });
  const rows = data?.content || [];
  const totalPages = data?.totalPages || 0;

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Analysis history</h1>
          <p className="mt-1 text-sm text-slate-500">
            AI listing analyses for this account{data ? ` · ${data.totalElements} total` : ""}.
          </p>
        </div>
      </div>
      <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="p-3">Product</th>
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
                  Loading analysis history...
                </td>
              </tr>
            )}
            {isError && (
              <tr>
                <td className="p-6" colSpan={7}>
                  <p className="text-rose-700">{apiErrorMessage(error, "Could not load analysis history")}</p>
                  <button type="button" className="mt-2 text-sm text-teal-700" onClick={() => refetch()}>
                    Retry
                  </button>
                </td>
              </tr>
            )}
            {!isLoading && !isError && rows.length === 0 && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={7}>
                  No analyses yet. Generate a listing from the Chrome extension or product analyze screen.
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
                    <span>{row.productName || "Draft"}</span>
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
                      View
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
            Previous
          </button>
          <span>
            Page {page + 1} of {totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((current) => current + 1)}
            className="rounded-lg border px-3 py-1.5 disabled:opacity-50"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
