import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import { useI18n } from "../../i18n/LanguageProvider";
import { BillPdfButton } from "./BillPdfButton";
import {
  formatDateTime,
  formatInr,
  ORDER_STATUS_STYLE,
  type PageResult,
  type WholesaleOrder,
  type WholesaleStore,
} from "./wholesale";

const FILTERS = ["", "PLACED", "ACCEPTED", "FULFILLED", "REJECTED", "CANCELLED"] as const;

export function SupplierOrdersPage() {
  const { t } = useI18n();
  const [status, setStatus] = useState<string>("PLACED");
  const [page, setPage] = useState(0);

  const store = useQuery({
    queryKey: ["ws-my-store"],
    queryFn: async () => ((await api.get("/wholesale/supplier/store")).data.data as WholesaleStore | undefined) ?? null,
    retry: false,
  });

  const orders = useQuery({
    queryKey: ["ws-supplier-orders", status, page],
    queryFn: async () =>
      (
        await api.get("/wholesale/supplier/orders", { params: { status: status || undefined, page, size: 10 } })
      ).data.data as PageResult<WholesaleOrder>,
    retry: false,
  });

  // Defence in depth: only render rows that belong to this supplier's store.
  const items = useMemo(() => {
    const all = orders.data?.items || [];
    const storeId = store.data?.id;
    if (!storeId) return all;
    return all.filter((order) => !order.storeId || order.storeId === storeId);
  }, [orders.data?.items, store.data?.id]);

  const storeName = store.data?.storeName;
  const httpStatus = axios.isAxiosError(orders.error) ? orders.error.response?.status : undefined;
  if (httpStatus === 403) {
    return (
      <div className="mx-auto max-w-2xl space-y-3 rounded-2xl border border-amber-200 bg-amber-50 p-6 text-sm text-amber-950">
        <p className="text-base font-semibold">{t("supplierOrders.noStore")}</p>
        <p>{apiErrorMessage(orders.error)}</p>
        <Link to="/supplier/store" className="inline-block rounded-xl bg-teal-700 px-4 py-2 font-semibold text-white">
          {t("supplierInv.openStore")}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{t("supplierOrders.title")}</h1>
          <p className="text-sm text-slate-500">{t("supplierOrders.subtitle")}</p>
          {storeName && (
            <p className="mt-2 rounded-lg bg-teal-50 px-3 py-2 text-sm font-medium text-teal-900 dark:bg-teal-950/40 dark:text-teal-200">
              Showing incoming orders for <span className="font-semibold">{storeName}</span> only. Other stores&apos; orders
              never appear here.
            </p>
          )}
          <p className="mt-2 text-xs text-slate-500">
            Looking for purchases you made from other sellers? Use{" "}
            <Link to="/live-stock/orders" className="font-medium text-teal-700 hover:underline">
              My bookings
            </Link>
            . Invoice history &amp; Excel:{" "}
            <Link to="/supplier/bills" className="font-medium text-teal-700 hover:underline">
              Store bills
            </Link>
            . Offline walk-in sales:{" "}
            <Link to="/supplier/inventory" className="font-medium text-teal-700 hover:underline">
              Supplier inventory → Manual bill
            </Link>
            .
          </p>
        </div>
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
            {value ? value.charAt(0) + value.slice(1).toLowerCase() : t("common.all")}
          </button>
        ))}
      </div>

      {orders.isLoading && <p className="text-sm text-slate-500">{t("supplierOrders.loading")}</p>}
      {orders.isError && <p className="text-sm text-red-600">{apiErrorMessage(orders.error)}</p>}
      {!orders.isLoading && items.length === 0 && (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900">
          {storeName ? `No orders for ${storeName} yet.` : t("supplierOrders.empty")}
        </div>
      )}

      {items.map((order) => (
        <SupplierOrderCard key={order.id} order={order} storeName={storeName || order.storeName || undefined} />
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

function SupplierOrderCard({ order, storeName }: { order: WholesaleOrder; storeName?: string }) {
  const qc = useQueryClient();
  const [note, setNote] = useState("");
  const [prices, setPrices] = useState<Record<string, string>>({});
  const [editing, setEditing] = useState(false);
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
          <p className="font-semibold">
            {order.orderNumber}
            {order.source === "MANUAL" && (
              <span className="ml-2 rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-slate-600">
                Manual
              </span>
            )}
          </p>
          <p className="text-xs text-slate-500">
            {storeName ? `${storeName} · ` : ""}
            Placed {formatDateTime(order.placedAt)} · {pieces} pcs
          </p>
        </div>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${ORDER_STATUS_STYLE[order.status] || "bg-slate-100"}`}>
          {order.status}
        </span>
      </header>

      {order.buyer && (
        <div className="rounded-xl bg-slate-50 px-3 py-2 text-sm dark:bg-slate-800">
          <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Buyer (customer who booked)</p>
          <p className="mt-0.5">
            <span className="font-medium">{order.buyer.name || "Buyer"}</span>
            {order.buyer.email && (
              <>
                {" · "}
                <a href={`mailto:${order.buyer.email}`} className="text-teal-700 hover:underline">
                  {order.buyer.email}
                </a>
              </>
            )}
            {order.buyer.mobile && (
              <>
                {" · "}
                <a href={`tel:${order.buyer.mobile}`} className="text-teal-700 hover:underline">
                  {order.buyer.mobile}
                </a>
              </>
            )}
          </p>
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

      <div className="space-y-1 rounded-xl bg-slate-50 px-3 py-2 text-sm dark:bg-slate-800">
        <div className="flex justify-between">
          <span>Subtotal</span>
          <span>{formatInr(order.subtotal)}</span>
        </div>
        {(order.discountAmount || 0) > 0 && (
          <div className="flex justify-between text-emerald-700">
            <span>Discount{order.couponCode ? ` (${order.couponCode})` : ""}</span>
            <span>-{formatInr(order.discountAmount)}</span>
          </div>
        )}
        {order.taxableAmount != null && (
          <div className="flex justify-between">
            <span>Taxable</span>
            <span>{formatInr(order.taxableAmount)}</span>
          </div>
        )}
        {(order.cgstAmount || 0) > 0 && (
          <div className="flex justify-between">
            <span>CGST</span>
            <span>{formatInr(order.cgstAmount)}</span>
          </div>
        )}
        {(order.sgstAmount || 0) > 0 && (
          <div className="flex justify-between">
            <span>SGST</span>
            <span>{formatInr(order.sgstAmount)}</span>
          </div>
        )}
        <div className="flex justify-between font-semibold">
          <span>Bill total</span>
          <span>{formatInr(order.acceptedTotal ?? order.grandTotal ?? order.subtotal)}</span>
        </div>
      </div>
      {order.buyerNote && <p className="text-xs text-slate-500">Buyer note: {order.buyerNote}</p>}
      {order.supplierNote && !open && <p className="text-xs text-slate-500">Your note: {order.supplierNote}</p>}

      <div className="flex flex-wrap gap-2">
        {order.billPdfUrl && (
          <BillPdfButton
            url={order.billPdfUrl}
            orderNumber={order.orderNumber}
            className="inline-flex rounded-xl border border-teal-700 px-4 py-2 text-sm font-semibold text-teal-800 hover:bg-teal-50 disabled:opacity-50"
          />
        )}
        {(order.status === "ACCEPTED" || order.status === "FULFILLED") && (
          <button
            type="button"
            onClick={() => setEditing((v) => !v)}
            className="rounded-xl border px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 dark:text-slate-200"
          >
            {editing ? "Close editor" : "Edit bill"}
          </button>
        )}
      </div>

      {editing && (
        <EditBillForm
          order={order}
          onDone={() => {
            setEditing(false);
            void refresh();
          }}
          onCancel={() => setEditing(false)}
        />
      )}

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

function EditBillForm({
  order,
  onDone,
  onCancel,
}: {
  order: WholesaleOrder;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [buyerName, setBuyerName] = useState(order.buyerName || order.buyer?.name || "");
  const [buyerMobile, setBuyerMobile] = useState(order.buyerMobile || order.buyer?.mobile || "");
  const [buyerEmail, setBuyerEmail] = useState(order.buyerEmail || order.buyer?.email || "");
  const [buyerGstin, setBuyerGstin] = useState(order.buyerGstin || order.buyer?.gstin || "");
  const [buyerAddress, setBuyerAddress] = useState(order.buyerAddress || order.buyer?.address || "");
  const [gstPercent, setGstPercent] = useState(String(order.gstPercent ?? 18));
  const [note, setNote] = useState(order.supplierNote || "");
  const [lines, setLines] = useState(
    order.items.map((item) => ({
      itemId: item.id,
      title: item.productTitle,
      quantity: item.quantity,
      unitPrice: String(item.acceptedUnitPrice ?? item.unitPrice),
      hsnSac: item.hsnSac || "",
    })),
  );

  const save = useMutation({
    mutationFn: async () =>
      (
        await api.put(`/wholesale/supplier/orders/${order.id}/bill`, {
          buyerName: buyerName.trim(),
          buyerMobile: buyerMobile.trim(),
          buyerEmail: buyerEmail.trim() || undefined,
          buyerGstin: buyerGstin.trim() || undefined,
          buyerAddress: buyerAddress.trim() || undefined,
          gstPercent: Number(gstPercent),
          supplierNote: note.trim() || undefined,
          lines: lines.map((line) => ({
            itemId: line.itemId,
            unitPrice: Number(line.unitPrice),
            hsnSac: line.hsnSac.trim() || undefined,
          })),
        })
      ).data.data as WholesaleOrder,
    onSuccess: onDone,
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!buyerName.trim() || !buyerMobile.trim()) return;
    save.mutate();
  };

  const field =
    "w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-teal-600 dark:border-slate-700 dark:bg-slate-900";

  return (
    <form onSubmit={onSubmit} className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4 dark:border-slate-700 dark:bg-slate-800/50">
      <h3 className="text-sm font-semibold">Edit tax invoice</h3>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm">
          <span className="mb-1 block font-medium">Customer name</span>
          <input required value={buyerName} onChange={(e) => setBuyerName(e.target.value)} className={field} />
        </label>
        <label className="block text-sm">
          <span className="mb-1 block font-medium">Mobile</span>
          <input required value={buyerMobile} onChange={(e) => setBuyerMobile(e.target.value)} className={field} />
        </label>
        <label className="block text-sm">
          <span className="mb-1 block font-medium">Email</span>
          <input type="email" value={buyerEmail} onChange={(e) => setBuyerEmail(e.target.value)} className={field} />
        </label>
        <label className="block text-sm">
          <span className="mb-1 block font-medium">Buyer GSTIN</span>
          <input value={buyerGstin} onChange={(e) => setBuyerGstin(e.target.value)} className={field} />
        </label>
        <label className="block text-sm sm:col-span-2">
          <span className="mb-1 block font-medium">Buyer address</span>
          <input value={buyerAddress} onChange={(e) => setBuyerAddress(e.target.value)} className={field} />
        </label>
        <label className="block text-sm">
          <span className="mb-1 block font-medium">GST %</span>
          <input
            required
            type="number"
            min={0}
            max={100}
            step="0.01"
            value={gstPercent}
            onChange={(e) => setGstPercent(e.target.value)}
            className={field}
          />
        </label>
      </div>

      <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-700">
        <table className="w-full min-w-[520px] text-left text-sm">
          <thead className="bg-white text-xs text-slate-500 dark:bg-slate-900">
            <tr>
              <th className="px-2 py-2">Item</th>
              <th>Qty</th>
              <th>HSN/SAC</th>
              <th className="text-right">Unit price</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((line) => (
              <tr key={line.itemId} className="border-t border-slate-100 dark:border-slate-800">
                <td className="px-2 py-2">{line.title}</td>
                <td>{line.quantity}</td>
                <td>
                  <input
                    value={line.hsnSac}
                    onChange={(e) =>
                      setLines((rows) =>
                        rows.map((r) => (r.itemId === line.itemId ? { ...r, hsnSac: e.target.value } : r)),
                      )
                    }
                    className="w-24 rounded border px-2 py-1 dark:border-slate-700 dark:bg-slate-900"
                  />
                </td>
                <td className="text-right">
                  <input
                    type="number"
                    min={0}
                    step="0.01"
                    value={line.unitPrice}
                    onChange={(e) =>
                      setLines((rows) =>
                        rows.map((r) => (r.itemId === line.itemId ? { ...r, unitPrice: e.target.value } : r)),
                      )
                    }
                    className="w-24 rounded border px-2 py-1 text-right dark:border-slate-700 dark:bg-slate-900"
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <label className="block text-sm">
        <span className="mb-1 block font-medium">Supplier note</span>
        <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} className={field} />
      </label>

      {save.isError && <p className="text-sm text-red-600">{apiErrorMessage(save.error)}</p>}

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={save.isPending}
          className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50"
        >
          {save.isPending ? "Saving…" : "Save & regenerate PDF"}
        </button>
        <button type="button" onClick={onCancel} className="rounded-xl border px-4 py-2 text-sm font-semibold">
          Cancel
        </button>
      </div>
    </form>
  );
}
