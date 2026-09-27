import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download } from "lucide-react";
import { api, apiErrorMessage } from "../../api/client";
import type { TransactionRow } from "../../types";

type TransactionPage = {
  content: TransactionRow[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export function TransactionHistoryPage() {
  const [page, setPage] = useState(0);
  const [downloading, setDownloading] = useState<string | null>(null);
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["transactions", page],
    queryFn: async () => (await api.get("/transactions", { params: { page, size: 20 } })).data.data as TransactionPage,
  });
  const rows = data?.content || [];
  const totalPages = data?.totalPages || 0;

  async function downloadBill(row: TransactionRow) {
    setDownloading(row.id);
    try {
      const html = (await api.get(`/transactions/${row.id}/bill`, { responseType: "text" })).data as string;
      const blob = new Blob([html], { type: "text/html" });
      const url = URL.createObjectURL(blob);
      const win = window.open(url, "_blank", "noopener,noreferrer");
      if (!win) {
        const link = document.createElement("a");
        link.href = url;
        link.download = `${row.invoiceNumber || `bill-${row.id}`}.html`;
        link.click();
      }
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      window.alert(apiErrorMessage(err, "Could not download bill"));
    } finally {
      setDownloading(null);
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Transaction history</h1>
        <p className="mt-1 text-sm text-slate-500">
          Plan charges, checkout attempts, and tax invoices for this account
          {data ? ` · ${data.totalElements} total` : ""}.
        </p>
      </div>
      <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
        <table className="w-full min-w-[980px] text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="p-3">Date</th>
              <th>Plan</th>
              <th>Type</th>
              <th>Status</th>
              <th>Amount</th>
              <th>Promo</th>
              <th>Provider</th>
              <th>Reference</th>
              <th>Bill</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={9}>
                  Loading transactions...
                </td>
              </tr>
            )}
            {isError && (
              <tr>
                <td className="p-6" colSpan={9}>
                  <p className="text-rose-700">{apiErrorMessage(error, "Could not load transactions")}</p>
                  <button type="button" className="mt-2 text-sm text-teal-700" onClick={() => refetch()}>
                    Retry
                  </button>
                </td>
              </tr>
            )}
            {!isLoading && !isError && rows.length === 0 && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={9}>
                  No transactions yet. Recharge from Subscription to create a payment record.
                </td>
              </tr>
            )}
            {rows.map((row) => (
              <tr key={row.id} className="border-t align-top">
                <td className="p-3">{row.createdAt ? new Date(row.createdAt).toLocaleString("en-IN") : "—"}</td>
                <td>{row.plan || "—"}</td>
                <td>{label(row.type)}</td>
                <td>
                  <span className={statusClass(row.status)}>{label(row.status)}</span>
                </td>
                <td>
                  <p>
                    {row.currency === "INR" ? "₹" : `${row.currency || ""} `}
                    {Number(row.amount || 0).toLocaleString("en-IN", { minimumFractionDigits: 2 })}
                  </p>
                  {(Number(row.discountAmount) > 0 || Number(row.gstAmount) > 0) && (
                    <p className="mt-0.5 text-[11px] text-slate-500">
                      Base ₹{Number(row.baseAmount || 0).toLocaleString("en-IN")}
                      {Number(row.discountAmount) > 0 ? ` · −₹${Number(row.discountAmount).toLocaleString("en-IN")}` : ""}
                      {Number(row.serviceCharge) > 0 ? ` · SC ₹${Number(row.serviceCharge).toLocaleString("en-IN")}` : ""}
                      {Number(row.gstAmount) > 0 ? ` · GST ₹${Number(row.gstAmount).toLocaleString("en-IN")}` : ""}
                    </p>
                  )}
                </td>
                <td>{row.promoCode || "—"}</td>
                <td>{row.provider || "—"}</td>
                <td className="max-w-[160px] truncate font-mono text-xs" title={row.reference}>
                  {row.reference || "—"}
                </td>
                <td className="p-3">
                  {row.billAvailable ? (
                    <button
                      type="button"
                      disabled={downloading === row.id}
                      onClick={() => downloadBill(row)}
                      className="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1.5 text-xs font-medium text-teal-800 hover:bg-teal-50 disabled:opacity-60"
                    >
                      <Download size={14} />
                      {downloading === row.id ? "Opening…" : "Bill"}
                    </button>
                  ) : (
                    <span className="text-slate-400">—</span>
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

function label(value?: string) {
  if (!value) return "—";
  return value
    .toLowerCase()
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}

function statusClass(status?: string) {
  const value = (status || "").toUpperCase();
  if (value === "SUCCESS") return "text-emerald-700";
  if (value === "REPORTED" || value === "PENDING") return "text-amber-700";
  if (value === "FAILED" || value === "EXPIRED") return "text-rose-700";
  return "text-slate-700";
}
