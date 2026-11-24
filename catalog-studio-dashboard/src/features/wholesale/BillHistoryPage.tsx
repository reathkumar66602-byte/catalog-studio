import { useMutation, useQuery } from "@tanstack/react-query";
import { Download, FileSpreadsheet, Receipt } from "lucide-react";
import { useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import { BillPdfButton } from "./BillPdfButton";
import { formatDateTime, formatInr, ORDER_STATUS_STYLE, type PageResult } from "./wholesale";

type BillRow = {
  id: string;
  orderNumber: string;
  status: string;
  source?: string | null;
  billDate: string;
  counterpartyName?: string | null;
  counterpartyEmail?: string | null;
  counterpartyMobile?: string | null;
  storeName?: string | null;
  subtotal: number;
  discountAmount?: number | null;
  taxableAmount?: number | null;
  cgstAmount?: number | null;
  sgstAmount?: number | null;
  gstPercent?: number | null;
  grandTotal: number;
  billPdfUrl?: string | null;
};

type BillHistory = {
  fromDate: string;
  toDate: string;
  storeName?: string | null;
  billCount: number;
  totalSubtotal: number;
  totalTax: number;
  totalAmount: number;
  page: PageResult<BillRow>;
};

function isoDate(d: Date) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function startOfMonth(d = new Date()) {
  return isoDate(new Date(d.getFullYear(), d.getMonth(), 1));
}

function startOfLastMonth() {
  const d = new Date();
  return isoDate(new Date(d.getFullYear(), d.getMonth() - 1, 1));
}

function endOfLastMonth() {
  const d = new Date();
  return isoDate(new Date(d.getFullYear(), d.getMonth(), 0));
}

type Role = "buyer" | "supplier";

const PRESETS = [
  { id: "this-month", label: "This month" },
  { id: "last-month", label: "Last month" },
  { id: "custom", label: "Custom range" },
] as const;

type PresetId = (typeof PRESETS)[number]["id"];

export function BuyerBillHistoryPage() {
  return <BillHistoryView role="buyer" />;
}

export function SupplierBillHistoryPage() {
  return <BillHistoryView role="supplier" />;
}

function BillHistoryView({ role }: { role: Role }) {
  const apiBase = role === "buyer" ? "/wholesale/buyer/bills" : "/wholesale/supplier/bills";
  const [preset, setPreset] = useState<PresetId>("this-month");
  const [from, setFrom] = useState(startOfMonth());
  const [to, setTo] = useState(isoDate(new Date()));
  const [page, setPage] = useState(0);

  const range = useMemo(() => {
    if (preset === "this-month") {
      return { from: startOfMonth(), to: isoDate(new Date()) };
    }
    if (preset === "last-month") {
      return { from: startOfLastMonth(), to: endOfLastMonth() };
    }
    return { from, to };
  }, [preset, from, to]);

  const history = useQuery({
    queryKey: ["ws-bills", role, range.from, range.to, page],
    queryFn: async () =>
      (
        await api.get(apiBase, {
          params: { from: range.from, to: range.to, page, size: 20 },
        })
      ).data.data as BillHistory,
  });

  const exportExcel = useMutation({
    mutationFn: async () => {
      const response = await api.get(`${apiBase}/export.xlsx`, {
        params: { from: range.from, to: range.to },
        responseType: "blob",
      });
      const blob = new Blob([response.data], {
        type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${role === "buyer" ? "my-bills" : "store-bills"}-${range.from}_to_${range.to}.xlsx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    },
  });

  const applyPreset = (id: PresetId) => {
    setPreset(id);
    setPage(0);
    if (id === "this-month") {
      setFrom(startOfMonth());
      setTo(isoDate(new Date()));
    } else if (id === "last-month") {
      setFrom(startOfLastMonth());
      setTo(endOfLastMonth());
    }
  };

  const data = history.data;
  const items = data?.page?.items || [];
  const title = role === "buyer" ? "My bills" : "Store bills";
  const subtitle =
    role === "buyer"
      ? "Invoices from wholesale stores you booked — filter by date and export to Excel."
      : "Accepted and fulfilled invoices for your store — filter by date and export to Excel.";

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{title}</h1>
          <p className="text-sm text-slate-500">{subtitle}</p>
          {data?.storeName && (
            <p className="mt-1 text-sm font-medium text-teal-800 dark:text-teal-300">Store: {data.storeName}</p>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            to={role === "buyer" ? "/live-stock/orders" : "/supplier/orders"}
            className="rounded-xl border px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            {role === "buyer" ? "My bookings" : "Store orders"}
          </Link>
          <button
            type="button"
            disabled={exportExcel.isPending || !data || data.billCount === 0}
            onClick={() => exportExcel.mutate()}
            className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50"
          >
            <FileSpreadsheet size={16} />
            {exportExcel.isPending ? "Preparing Excel…" : "Download Excel"}
          </button>
        </div>
      </div>

      <div className="space-y-3 rounded-2xl border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
        <div className="flex flex-wrap gap-2">
          {PRESETS.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => applyPreset(item.id)}
              className={`rounded-full border px-4 py-1.5 text-sm font-medium ${
                preset === item.id
                  ? "border-teal-700 bg-teal-700 text-white"
                  : "border-slate-200 bg-white text-slate-700 hover:border-teal-300 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
              }`}
            >
              {item.label}
            </button>
          ))}
        </div>

        {preset === "custom" && (
          <div className="grid gap-3 sm:grid-cols-[1fr_1fr_auto]">
            <label className="block text-sm">
              <span className="mb-1 block font-medium">From</span>
              <input
                type="date"
                value={from}
                max={to}
                onChange={(e) => {
                  setFrom(e.target.value);
                  setPage(0);
                }}
                className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-900"
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-medium">To</span>
              <input
                type="date"
                value={to}
                min={from}
                max={isoDate(new Date())}
                onChange={(e) => {
                  setTo(e.target.value);
                  setPage(0);
                }}
                className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 dark:border-slate-700 dark:bg-slate-900"
              />
            </label>
            <div className="flex items-end text-xs text-slate-500 sm:pb-2">IST · max 2 years</div>
          </div>
        )}

        <p className="text-xs text-slate-500">
          Showing {range.from} → {range.to}
          {data ? ` · ${data.billCount} bill${data.billCount === 1 ? "" : "s"}` : ""}
        </p>
      </div>

      {data && (
        <div className="grid gap-3 sm:grid-cols-3">
          <SummaryCard label="Bills" value={String(data.billCount)} icon={<Receipt size={18} />} />
          <SummaryCard label="Tax (CGST+SGST)" value={formatInr(data.totalTax)} />
          <SummaryCard label="Grand total" value={formatInr(data.totalAmount)} highlight />
        </div>
      )}

      {exportExcel.isError && (
        <p className="text-sm text-red-600">{apiErrorMessage(exportExcel.error, "Excel export failed")}</p>
      )}
      {history.isLoading && <p className="text-sm text-slate-500">Loading bills…</p>}
      {history.isError && <p className="text-sm text-red-600">{apiErrorMessage(history.error)}</p>}

      {!history.isLoading && items.length === 0 && (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900">
          No bills in this period. Accepted / fulfilled invoices will appear here.
        </div>
      )}

      {items.length > 0 && (
        <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
          <table className="w-full min-w-[920px] text-left text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500 dark:bg-slate-800/80">
              <tr>
                <th className="px-3 py-3">Bill date</th>
                <th>Invoice</th>
                <th>{role === "buyer" ? "Store" : "Buyer"}</th>
                <th>Status</th>
                <th className="text-right">Taxable</th>
                <th className="text-right">Tax</th>
                <th className="text-right">Total</th>
                <th className="text-right">PDF</th>
              </tr>
            </thead>
            <tbody>
              {items.map((row) => {
                const tax = (row.cgstAmount || 0) + (row.sgstAmount || 0);
                return (
                  <tr key={row.id} className="border-t border-slate-100 dark:border-slate-800">
                    <td className="px-3 py-3 whitespace-nowrap">{formatDateTime(row.billDate)}</td>
                    <td>
                      <p className="font-medium">{row.orderNumber}</p>
                      {row.source === "MANUAL" && (
                        <span className="text-[10px] font-semibold uppercase tracking-wide text-slate-500">Manual</span>
                      )}
                    </td>
                    <td>
                      {role === "buyer" ? (
                        <span>{row.storeName || "—"}</span>
                      ) : (
                        <div>
                          <p className="font-medium">{row.counterpartyName || "—"}</p>
                          <p className="text-xs text-slate-500">
                            {[row.counterpartyEmail, row.counterpartyMobile].filter(Boolean).join(" · ") || "—"}
                          </p>
                        </div>
                      )}
                    </td>
                    <td>
                      <span
                        className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                          ORDER_STATUS_STYLE[row.status] || "bg-slate-100"
                        }`}
                      >
                        {row.status}
                      </span>
                    </td>
                    <td className="text-right tabular-nums">{formatInr(row.taxableAmount ?? row.subtotal)}</td>
                    <td className="text-right tabular-nums">{formatInr(tax)}</td>
                    <td className="text-right font-semibold tabular-nums">{formatInr(row.grandTotal)}</td>
                    <td className="px-3 text-right">
                      {row.billPdfUrl ? (
                        <BillPdfButton
                          url={row.billPdfUrl}
                          orderNumber={row.orderNumber}
                          className="inline-flex items-center gap-1 rounded-lg border border-teal-700 px-2.5 py-1 text-xs font-semibold text-teal-800 hover:bg-teal-50 disabled:opacity-50"
                        >
                          <Download size={12} /> PDF
                        </BillPdfButton>
                      ) : (
                        <span className="text-xs text-slate-400">—</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {data?.page && data.page.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-lg border px-3 py-1.5 disabled:opacity-40"
          >
            Previous
          </button>
          <span className="text-slate-500">
            Page {data.page.page + 1} of {data.page.totalPages}
          </span>
          <button
            type="button"
            disabled={!data.page.hasNext}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-lg border px-3 py-1.5 disabled:opacity-40"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}

function SummaryCard({
  label,
  value,
  icon,
  highlight,
}: {
  label: string;
  value: string;
  icon?: ReactNode;
  highlight?: boolean;
}) {
  return (
    <div
      className={`rounded-2xl border px-4 py-3 ${
        highlight
          ? "border-teal-200 bg-teal-50 dark:border-teal-900 dark:bg-teal-950/30"
          : "border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
      }`}
    >
      <p className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-slate-500">
        {icon}
        {label}
      </p>
      <p className="mt-1 text-xl font-semibold tabular-nums">{value}</p>
    </div>
  );
}
