import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { useState } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import {
  formatDateTime,
  formatInr,
  ORDER_STATUS_STYLE,
  type PageResult,
  type WholesaleOrder,
} from "./wholesale";

const FILTERS = ["", "PLACED", "ACCEPTED", "FULFILLED", "REJECTED", "CANCELLED"] as const;

export function SupplierOrdersPage() {
  const [status, setStatus] = useState<string>("PLACED");
  const [page, setPage] = useState(0);

  const orders = useQuery({
    queryKey: ["ws-supplier-orders", status, page],
    queryFn: async () =>
      (
        await api.get("/wholesale/supplier/orders", { params: { status: status || undefined, page, size: 10 } })
      ).data.data as PageResult<WholesaleOrder>,
    retry: false,
  });

  const httpStatus = axios.isAxiosError(orders.error) ? orders.error.response?.status : undefined;
  if (httpStatus === 403) {
    return (
      <div className="mx-auto max-w-2xl space-y-3 rounded-2xl border border-amber-200 bg-amber-50 p-6 text-sm text-amber-950">
        <p className="text-base font-semibold">No supplier store yet</p>
        <p>{apiErrorMessage(orders.error)}</p>
        <Link to="/supplier/store" className="inline-block rounded-xl bg-teal-700 px-4 py-2 font-semibold text-white">
          Open my store
        </Link>
      </div>
    );
  }

  const items = orders.data?.items || [];

  return (
    <div className="mx-auto max-w-4xl space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">Supplier orders</h1>
        <p className="text-sm text-slate-500">
          Accepting an order deducts stock and shares your phone and email with the buyer.
        </p>
      </div>

      <div className="flex gap-2 overflow-x-auto">
        {FILTERS.map((value) => (
          <button
            key={value || "all"}
            type="button"
            onClick={() => {
              setStatus(value);
              setPage(0);
            }}
            className={`shrink-0 rounded-full border px-4 py-1.5 text-sm font-medium ${
              status === value
                ? "border-teal-700 bg-teal-700 text-white"
                : "border-slate-200 bg-white text-slate-700 hover:border-teal-300 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            }`}
          >
            {value ? value.charAt(0) + value.slice(1).toLowerCase() : "All"}
          </button>
        ))}
      </div>

      {orders.isLoading && <p className="text-sm text-slate-500">Loading orders...</p>}
      {orders.isError && <p className="text-sm text-red-600">{apiErrorMessage(orders.error)}</p>}
      {!orders.isLoading && items.length === 0 && (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900">
          No orders in this view.
        </div>
      )}

      {items.map((order) => (
        <SupplierOrderCard key={order.id} order={order} />
      ))}

      {orders.data && orders.data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))} className="rounded-lg border px-3 py-1.5 disabled:opacity-40">
            Previous
          </button>
          <span className="text-slate-500">
            Page {orders.data.page + 1} of {orders.data.totalPages}
          </span>
          <button type="button" disabled={!orders.data.hasNext} onClick={() => setPage((p) => p + 1)} className="rounded-lg border px-3 py-1.5 disabled:opacity-40">
            Next
          </button>
        </div>
      )}
    </div>
  );
}

function SupplierOrderCard({ order }: { order: WholesaleOrder }) {
  const qc = useQueryClient();
  const [note, setNote] = useState("");
  const [prices, setPrices] = useState<Record<string, string>>({});
  const open = order.status === "PLACED";

  const refresh = async () => {
    await qc.invalidateQueries({ queryKey: ["ws-supplier-orders"] });
    await qc.invalidateQueries({ queryKey: ["ws-my-products"] });
  };

  const accept = useMutation({
    mutationFn: async () => {
      const lines = order.items
        .filter((item) => prices[item.id] !== undefined && prices[item.id] !== "" && Number(prices[item.id]) !== item.unitPrice)
        .map((item) => ({ itemId: item.id, unitPrice: Number(prices[item.id]) }));
      return api.post(`/wholesale/supplier/orders/${order.id}/accept`, {
        supplierNote: note.trim() || undefined,
        lines: lines.length ? lines : undefined,
      });
    },
    onSuccess: refresh,
  });
  const reject = useMutation({
    mutationFn: () => api.post(`/wholesale/supplier/orders/${order.id}/reject`, { supplierNote: note.trim() || undefined }),
    onSuccess: refresh,
  });
  const fulfill = useMutation({
    mutationFn: () => api.post(`/wholesale/supplier/orders/${order.id}/fulfill`),
    onSuccess: refresh,
  });

  const busy = accept.isPending || reject.isPending || fulfill.isPending;
  const error = accept.error || reject.error || fulfill.error;
  const pieces = order.items.reduce((sum, item) => sum + item.quantity, 0);

  return (
    <article className="space-y-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900">
      <header className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-semibold">{order.orderNumber}</p>
          <p className="text-xs text-slate-500">
            Placed {formatDateTime(order.placedAt)} - {pieces} pcs
          </p>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${ORDER_STATUS_STYLE[order.status] || "bg-slate-100"}`}>
          {order.status}
        </span>
      </header>

      {order.buyer && (
        <div className="rounded-xl bg-slate-50 px-3 py-2 text-sm dark:bg-slate-800">
          <span className="font-medium">{order.buyer.name || "Buyer"}</span>
          {order.buyer.email && (
            <>
              {" - "}
              <a href={`mailto:${order.buyer.email}`} className="text-teal-700 hover:underline">
                {order.buyer.email}
              </a>
            </>
          )}
          {order.buyer.mobile && (
            <>
              {" - "}
              <a href={`tel:${order.buyer.mobile}`} className="text-teal-700 hover:underline">
                {order.buyer.mobile}
              </a>
            </>
          )}
        </div>
      )}

      <div className="overflow-x-auto">
        <table className="w-full min-w-[520px] text-left text-sm">
          <thead className="text-xs text-slate-500">
            <tr>
              <th className="py-1">Item</th>
              <th>Colour / Size</th>
              <th className="text-right">Qty</th>
              <th className="text-right">{open ? "Unit price (editable)" : "Unit price"}</th>
              <th className="text-right">Total</th>
            </tr>
          </thead>
          <tbody>
            {order.items.map((item) => {
              const unit = item.acceptedUnitPrice ?? item.unitPrice;
              return (
                <tr key={item.id} className="border-t border-slate-100 dark:border-slate-800">
                  <td className="py-1.5">{item.productTitle}</td>
                  <td>{[item.colorLabel, item.sizeLabel].filter(Boolean).join(" / ")}</td>
                  <td className="text-right">{item.quantity}</td>
                  <td className="text-right">
                    {open ? (
                      <input
                        type="number"
                        min={0}
                        step="0.01"
                        value={prices[item.id] ?? String(item.unitPrice)}
                        onChange={(e) => setPrices((p) => ({ ...p, [item.id]: e.target.value }))}
                        className="w-24 rounded-lg border px-2 py-1 text-right dark:border-slate-700 dark:bg-slate-900"
                      />
                    ) : (
                      formatInr(unit)
                    )}
                  </td>
                  <td className="text-right">{formatInr(unit * item.quantity)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <p className="text-sm font-semibold">
        Total: {formatInr(order.acceptedTotal ?? order.subtotal)}
        {order.acceptedTotal != null && order.acceptedTotal !== order.subtotal && (
          <span className="ml-2 text-xs font-normal text-slate-400 line-through">{formatInr(order.subtotal)}</span>
        )}
      </p>
      {order.buyerNote && <p className="text-xs text-slate-500">Buyer note: {order.buyerNote}</p>}
      {order.supplierNote && !open && <p className="text-xs text-slate-500">Your note: {order.supplierNote}</p>}

      {open && (
        <div className="space-y-2">
          <textarea
            rows={2}
            maxLength={1000}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Note for the buyer (optional)"
            className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-teal-600 dark:border-slate-700 dark:bg-slate-900"
          />
          <div className="flex gap-2">
            <button
              type="button"
              disabled={busy}
              onClick={() => accept.mutate()}
              className="rounded-xl bg-teal-700 px-5 py-2 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50"
            >
              {accept.isPending ? "Accepting..." : "Accept order"}
            </button>
            <button
              type="button"
              disabled={busy}
              onClick={() => {
                if (window.confirm("Reject this order and release the reserved stock?")) reject.mutate();
              }}
              className="rounded-xl border border-red-200 px-5 py-2 text-sm font-semibold text-red-700 hover:bg-red-50 disabled:opacity-50"
            >
              Reject
            </button>
          </div>
        </div>
      )}

      {order.status === "ACCEPTED" && (
        <button
          type="button"
          disabled={busy}
          onClick={() => fulfill.mutate()}
          className="rounded-xl border border-teal-700 px-4 py-2 text-sm font-semibold text-teal-800 hover:bg-teal-50 disabled:opacity-50"
        >
          Mark fulfilled
        </button>
      )}
      {error && <p className="text-sm text-red-600">{apiErrorMessage(error)}</p>}
    </article>
  );
}
