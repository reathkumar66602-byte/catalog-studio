import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, Search } from "lucide-react";
import { api, apiErrorMessage } from "../../api/client";

type AdminPaymentRow = {
  id: string;
  userId?: string;
  userEmail: string;
  userName?: string;
  reference?: string;
  type: string;
  status: string;
  plan: string;
  amount: number;
  currency: string;
  provider: string;
  billingCycle?: string;
  notes?: string;
  createdAt: string;
  paidAt?: string | null;
  baseAmount?: number;
  serviceCharge?: number;
  gstAmount?: number;
  discountAmount?: number;
  promoCode?: string | null;
  companyGstin?: string | null;
  invoiceNumber?: string | null;
  gatewayOrderId?: string | null;
  gatewayPaymentId?: string | null;
  billAvailable: boolean;
};

type PageResp = {
  content: AdminPaymentRow[];
  totalElements: number;
  totalPages: number;
  number: number;
};

function inr(value?: number | null) {
  return `₹${Number(value || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function AdminPaymentsPage() {
  const [q, setQ] = useState("");
  const [provider, setProvider] = useState("");
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const [page, setPage] = useState(0);
  const [downloading, setDownloading] = useState<string | null>(null);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["admin-payments", q, provider, status, type, page],
    queryFn: async () =>
      (
        await api.get("/admin/billing/payments", {
          params: { q: q || undefined, provider: provider || undefined, status: status || undefined, type: type || undefined, page, size: 25 },
        })
      ).data.data as PageResp,
  });

  async function openBill(row: AdminPaymentRow) {
    setDownloading(row.id);
    try {
      const html = (await api.get(`/admin/billing/payments/${row.id}/bill`, { responseType: "text" })).data as string;
      const blob = new Blob([html], { type: "text/html" });
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener,noreferrer");
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      window.alert(apiErrorMessage(err, "Could not open invoice"));
    } finally {
      setDownloading(null);
    }
  }

  const rows = data?.content || [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Payment ledger</h1>
        <p className="mt-1 text-sm text-slate-500">
          Company-wide Cashfree and manual payment records — who paid, plan, GST breakdown, invoice, and gateway references.
          {data ? ` · ${data.totalElements} rows` : ""}
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-2xl border border-slate-200 bg-white p-4">
        <label className="min-w-[220px] flex-1 text-sm font-medium">
          Search email / invoice / order
          <div className="relative mt-1">
            <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              value={q}
              onChange={(e) => {
                setPage(0);
                setQ(e.target.value);
              }}
              className="w-full rounded-xl border py-2 pl-9 pr-3"
              placeholder="seller@… or cs_… or CS-…"
            />
          </div>
        </label>
        <label className="text-sm font-medium">
          Provider
          <select
            value={provider}
            onChange={(e) => {
              setPage(0);
              setProvider(e.target.value);
            }}
            className="mt-1 block rounded-xl border px-3 py-2"
          >
            <option value="">All</option>
            <option value="CASHFREE">CASHFREE</option>
            <option value="MANUAL">MANUAL</option>
          </select>
        </label>
        <label className="text-sm font-medium">
          Status
          <select
            value={status}
            onChange={(e) => {
              setPage(0);
              setStatus(e.target.value);
            }}
            className="mt-1 block rounded-xl border px-3 py-2"
          >
            <option value="">All</option>
            <option value="PENDING">PENDING</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="FAILED">FAILED</option>
            <option value="EXPIRED">EXPIRED</option>
            <option value="REPORTED">REPORTED</option>
          </select>
        </label>
        <label className="text-sm font-medium">
          Type
          <select
            value={type}
            onChange={(e) => {
              setPage(0);
              setType(e.target.value);
            }}
            className="mt-1 block rounded-xl border px-3 py-2"
          >
            <option value="">All</option>
            <option value="CHECKOUT">CHECKOUT</option>
            <option value="ACTIVATION">ACTIVATION</option>
            <option value="PLAN_CHANGE">PLAN_CHANGE</option>
            <option value="DEACTIVATION">DEACTIVATION</option>
            <option value="TRIAL">TRIAL</option>
          </select>
        </label>
        <button type="button" onClick={() => refetch()} className="rounded-xl border px-4 py-2 text-sm font-medium">
          Refresh
        </button>
      </div>

      <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
        <table className="w-full min-w-[1200px] text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="p-3">When</th>
              <th>User</th>
              <th>Plan</th>
              <th>Type</th>
              <th>Status</th>
              <th>Amount</th>
              <th>GST</th>
              <th>Provider</th>
              <th>Gateway / ref</th>
              <th>Invoice</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={10}>
                  Loading ledger…
                </td>
              </tr>
            )}
            {isError && (
              <tr>
                <td className="p-6 text-red-600" colSpan={10}>
                  {apiErrorMessage(error, "Could not load payments")}
                </td>
              </tr>
            )}
            {!isLoading && !isError && rows.length === 0 && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={10}>
                  No payment rows match these filters.
                </td>
              </tr>
            )}
            {rows.map((row) => (
              <tr key={row.id} className="border-t border-slate-100 align-top">
                <td className="p-3 whitespace-nowrap text-slate-600">
                  {new Date(row.createdAt).toLocaleString()}
                  {row.paidAt && (
                    <div className="text-xs text-emerald-700">Paid {new Date(row.paidAt).toLocaleString()}</div>
                  )}
                </td>
                <td className="p-3">
                  <div className="font-medium text-slate-900">{row.userEmail}</div>
                  {row.userName && <div className="text-xs text-slate-500">{row.userName}</div>}
                </td>
                <td className="p-3">{row.plan}</td>
                <td className="p-3">{row.type}</td>
                <td className="p-3">{row.status}</td>
                <td className="p-3 font-medium">{inr(row.amount)}</td>
                <td className="p-3 text-slate-600">{inr(row.gstAmount)}</td>
                <td className="p-3">{row.provider}</td>
                <td className="p-3 font-mono text-xs text-slate-600">
                  <div>{row.gatewayOrderId || row.reference || "—"}</div>
                  {row.gatewayPaymentId && <div className="text-slate-400">{row.gatewayPaymentId}</div>}
                  {row.promoCode && <div className="text-amber-700">promo {row.promoCode}</div>}
                </td>
                <td className="p-3">
                  {row.billAvailable ? (
                    <button
                      type="button"
                      disabled={downloading === row.id}
                      onClick={() => openBill(row)}
                      className="inline-flex items-center gap-1 rounded-lg border px-2.5 py-1.5 text-xs font-medium"
                    >
                      <Download size={14} />
                      {row.invoiceNumber || "Bill"}
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

      {data && data.totalPages > 1 && (
        <div className="flex items-center gap-3">
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-lg border px-3 py-1.5 text-sm disabled:opacity-40"
          >
            Previous
          </button>
          <span className="text-sm text-slate-500">
            Page {data.number + 1} / {data.totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-lg border px-3 py-1.5 text-sm disabled:opacity-40"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}
