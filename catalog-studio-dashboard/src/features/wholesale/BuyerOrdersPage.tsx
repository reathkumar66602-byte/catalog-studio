import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Mail, Phone } from "lucide-react";
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

export function BuyerOrdersPage() {
  const qc = useQueryClient();
  const [page, setPage] = useState(0);

  const orders = useQuery({
    queryKey: ["ws-buyer-orders", page],
    queryFn: async () =>
      (await api.get("/wholesale/buyer/orders", { params: { page, size: 10 } })).data.data as PageResult<WholesaleOrder>,
  });

  const cancel = useMutation({
    mutationFn: (id: string) => api.post(`/wholesale/buyer/orders/${id}/cancel`),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["ws-buyer-orders"] });
      await qc.invalidateQueries({ queryKey: ["ws-products"] });
    },
  });

  const items = orders.data?.items || [];

  return (
    <div className="mx-auto max-w-4xl space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">My wholesale orders</h1>
          <p className="text-sm text-slate-500">
            Supplier phone and email appear here once the supplier accepts your order.
          </p>
        </div>
        <Link to="/live-stock" className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white">
          Browse Live Stock
        </Link>
      </div>

      {orders.isLoading && <p className="text-sm text-slate-500">Loading orders...</p>}
      {orders.isError && <p className="text-sm text-red-600">{apiErrorMessage(orders.error)}</p>}
      {cancel.isError && <p className="text-sm text-red-600">{apiErrorMessage(cancel.error)}</p>}

      {!orders.isLoading && items.length === 0 && (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900">
          You have not booked any stock yet.
        </div>
      )}

      {items.map((order) => (
        <article
          key={order.id}
          className="space-y-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900"
        >
          <header className="flex flex-wrap items-start justify-between gap-2">
            <div>
              <p className="font-semibold">{order.orderNumber}</p>
              <p className="text-xs text-slate-500">
                {order.storeName} - Placed {formatDateTime(order.placedAt)}
              </p>
            </div>
            <span className={`rounded-full px-3 py-1 text-xs font-semibold ${ORDER_STATUS_STYLE[order.status] || "bg-slate-100"}`}>
              {order.status}
            </span>
          </header>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[480px] text-left text-sm">
              <thead className="text-xs text-slate-500">
                <tr>
                  <th className="py-1">Item</th>
                  <th>Colour / Size</th>
                  <th className="text-right">Qty</th>
                  <th className="text-right">Price</th>
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
                        {item.acceptedUnitPrice != null && item.acceptedUnitPrice !== item.unitPrice ? (
                          <>
                            <span className="mr-1 text-xs text-slate-400 line-through">{formatInr(item.unitPrice)}</span>
                            {formatInr(unit)}
                          </>
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

          <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <span className="font-semibold">
              {order.acceptedTotal != null && order.acceptedTotal !== order.subtotal ? (
                <>
                  Accepted total: {formatInr(order.acceptedTotal)}{" "}
                  <span className="text-xs font-normal text-slate-400 line-through">{formatInr(order.subtotal)}</span>
                </>
              ) : (
                <>Total: {formatInr(order.acceptedTotal ?? order.subtotal)}</>
              )}
            </span>
            {order.status === "PLACED" && (
              <button
                type="button"
                disabled={cancel.isPending}
                onClick={() => {
                  if (window.confirm("Cancel this order and release the reserved stock?")) cancel.mutate(order.id);
                }}
                className="rounded-lg border border-red-200 px-3 py-1.5 text-xs font-semibold text-red-700 hover:bg-red-50 disabled:opacity-50"
              >
                Cancel order
              </button>
            )}
          </div>

          {order.buyerNote && <p className="text-xs text-slate-500">Your note: {order.buyerNote}</p>}
          {order.supplierNote && (
            <p className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600 dark:bg-slate-800 dark:text-slate-300">
              Supplier note: {order.supplierNote}
            </p>
          )}

          {order.supplierContact ? (
            <div className="rounded-xl border border-teal-200 bg-teal-50 px-4 py-3 text-sm dark:border-teal-900 dark:bg-teal-950/30">
              <p className="mb-1 font-semibold text-teal-900 dark:text-teal-200">Supplier contact</p>
              <div className="flex flex-wrap gap-x-6 gap-y-1">
                {order.supplierContact.phone && (
                  <a href={`tel:${order.supplierContact.phone}`} className="inline-flex items-center gap-1.5 text-teal-800 hover:underline">
                    <Phone size={14} /> {order.supplierContact.phone}
                  </a>
                )}
                {order.supplierContact.email && (
                  <a href={`mailto:${order.supplierContact.email}`} className="inline-flex items-center gap-1.5 text-teal-800 hover:underline">
                    <Mail size={14} /> {order.supplierContact.email}
                  </a>
                )}
              </div>
            </div>
          ) : (
            order.status === "PLACED" && (
              <p className="text-xs text-slate-500">Waiting for the supplier to accept. Contact details unlock after acceptance.</p>
            )
          )}
        </article>
      ))}

      {orders.data && orders.data.totalPages > 1 && (
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
            Page {orders.data.page + 1} of {orders.data.totalPages}
          </span>
          <button
            type="button"
            disabled={!orders.data.hasNext}
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
