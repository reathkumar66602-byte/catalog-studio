import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Mail, Plus, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import { BillPdfButton } from "./BillPdfButton";
import {
  formatInr,
  type PageResult,
  type ProductCard,
  type ProductDetail,
  type WholesaleOrder,
  type WholesaleStore,
} from "./wholesale";

const fieldClass =
  "w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100 dark:border-slate-700 dark:bg-slate-900";

type BillLine = {
  key: string;
  productId: string;
  productTitle: string;
  variantId: string;
  label: string;
  quantity: string;
  unitPrice: string;
  available: number;
};

type Props = {
  /** When set, product picker opens on this product. */
  initialProductId?: string;
  onDone?: (order: WholesaleOrder) => void;
  onCancel?: () => void;
};

function money(value: number) {
  return Math.round(value * 100) / 100;
}

export function ManualBillPanel({ initialProductId = "", onDone, onCancel }: Props) {
  const qc = useQueryClient();
  const [buyerName, setBuyerName] = useState("");
  const [buyerMobile, setBuyerMobile] = useState("");
  const [buyerEmail, setBuyerEmail] = useState("");
  const [gstPercent, setGstPercent] = useState("18");
  const [note, setNote] = useState("");
  const [productId, setProductId] = useState(initialProductId);
  const [lines, setLines] = useState<BillLine[]>([]);
  const [created, setCreated] = useState<WholesaleOrder | null>(null);

  const store = useQuery({
    queryKey: ["ws-my-store"],
    queryFn: async () => ((await api.get("/wholesale/supplier/store")).data.data as WholesaleStore | undefined) ?? null,
  });

  useEffect(() => {
    if (store.data?.gstPercent != null) {
      setGstPercent(String(store.data.gstPercent));
    }
  }, [store.data?.gstPercent]);

  useEffect(() => {
    if (initialProductId) setProductId(initialProductId);
  }, [initialProductId]);

  const products = useQuery({
    queryKey: ["ws-my-products", "manual-bill"],
    queryFn: async () =>
      (await api.get("/wholesale/supplier/products", { params: { status: "ACTIVE", page: 0, size: 100 } })).data
        .data as PageResult<ProductCard>,
  });

  const productDetail = useQuery({
    queryKey: ["ws-my-product", productId],
    enabled: !!productId,
    queryFn: async () => (await api.get(`/wholesale/supplier/products/${productId}`)).data.data as ProductDetail,
  });

  const create = useMutation({
    mutationFn: async () => {
      const items = lines
        .map((line) => ({
          variantId: line.variantId,
          quantity: Number(line.quantity),
          unitPrice: line.unitPrice === "" ? undefined : Number(line.unitPrice),
        }))
        .filter((item) => item.quantity > 0);
      return (
        await api.post("/wholesale/supplier/orders/manual", {
          buyerName: buyerName.trim(),
          buyerMobile: buyerMobile.trim(),
          buyerEmail: buyerEmail.trim() || undefined,
          gstPercent: Number(gstPercent),
          note: note.trim() || undefined,
          items,
        })
      ).data.data as WholesaleOrder;
    },
    onSuccess: async (order) => {
      setCreated(order);
      await qc.invalidateQueries({ queryKey: ["ws-supplier-orders"] });
      await qc.invalidateQueries({ queryKey: ["ws-my-products"] });
      onDone?.(order);
    },
  });

  const productOptions = products.data?.items || [];
  const variants = useMemo(
    () => (productDetail.data?.variants || []).filter((v) => v.status === "ACTIVE" && (v.available ?? 0) > 0),
    [productDetail.data],
  );

  const totals = useMemo(() => {
    const subtotal = money(
      lines.reduce((sum, line) => sum + Number(line.quantity || 0) * Number(line.unitPrice || 0), 0),
    );
    const gst = Math.min(100, Math.max(0, Number(gstPercent) || 0));
    const tax = money((subtotal * gst) / 100);
    const cgst = money(tax / 2);
    const sgst = money(tax - cgst);
    return { subtotal, gst, cgst, sgst, grandTotal: money(subtotal + tax) };
  }, [lines, gstPercent]);

  const addVariant = (variantId: string) => {
    const product = productDetail.data;
    const variant = variants.find((v) => v.id === variantId);
    if (!product || !variant) return;
    if (lines.some((l) => l.variantId === variantId)) return;
    setLines((current) => [
      ...current,
      {
        key: `${product.id}-${variant.id}`,
        productId: product.id,
        productTitle: product.title,
        variantId: variant.id,
        label: [variant.colorLabel, variant.sizeLabel].filter(Boolean).join(" / "),
        quantity: "1",
        unitPrice: String(variant.price),
        available: variant.available,
      },
    ]);
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!buyerName.trim() || !buyerMobile.trim()) return;
    if (lines.every((l) => Number(l.quantity) <= 0)) return;
    create.mutate();
  };

  if (created) {
    const emailed = Boolean(created.buyerEmail && created.billPdfUrl);
    return (
      <div className="space-y-4 rounded-2xl border border-emerald-200 bg-emerald-50/60 p-5 dark:border-emerald-900 dark:bg-emerald-950/20">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-emerald-800">Bill created</p>
          <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{created.orderNumber}</h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-300">
            Stock deducted for {store.data?.storeName || "your store"}. Total {formatInr(created.grandTotal ?? created.subtotal)}.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {created.billPdfUrl && (
            <BillPdfButton
              url={created.billPdfUrl}
              orderNumber={created.orderNumber}
              className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50"
            >
              Download PDF bill
            </BillPdfButton>
          )}
          <Link
            to="/supplier/orders"
            className="inline-flex items-center gap-2 rounded-xl border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-100"
          >
            View store orders
          </Link>
          {onCancel && (
            <button
              type="button"
              onClick={onCancel}
              className="rounded-xl px-4 py-2 text-sm font-semibold text-slate-600 hover:bg-white/60"
            >
              Close
            </button>
          )}
        </div>
        <p className="flex items-start gap-2 text-sm text-slate-600 dark:text-slate-300">
          <Mail size={16} className="mt-0.5 shrink-0" />
          {emailed
            ? `Invoice emailed to ${created.buyerEmail}.`
            : "No buyer email — bill saved as PDF only. You can download and share it offline."}
        </p>
      </div>
    );
  }

  return (
    <form
      onSubmit={onSubmit}
      className="space-y-5 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-teal-800 dark:text-teal-300">Offline sale</p>
          <h2 className="text-lg font-semibold">Manual bill</h2>
          <p className="mt-1 text-sm text-slate-500">
            For walk-in buyers without an app account. Stock is deducted now, GST is applied at the rate you enter, and a PDF
            invoice is generated{store.data?.storeName ? ` for ${store.data.storeName}` : ""}.
          </p>
        </div>
        {onCancel && (
          <button type="button" onClick={onCancel} className="rounded-lg border px-3 py-1.5 text-sm font-medium hover:bg-slate-50">
            Cancel
          </button>
        )}
      </div>

      <section className="space-y-3">
        <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">1. Customer</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Buyer name</span>
            <input required value={buyerName} onChange={(e) => setBuyerName(e.target.value)} className={fieldClass} />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Mobile</span>
            <input required value={buyerMobile} onChange={(e) => setBuyerMobile(e.target.value)} className={fieldClass} />
          </label>
          <label className="block text-sm sm:col-span-2">
            <span className="mb-1 block font-medium">Email (optional — send invoice)</span>
            <input
              type="email"
              value={buyerEmail}
              onChange={(e) => setBuyerEmail(e.target.value)}
              placeholder="Leave blank to save PDF only"
              className={fieldClass}
            />
          </label>
        </div>
      </section>

      <section className="space-y-3">
        <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">2. Items from your inventory</h3>
        <div className="grid gap-3 sm:grid-cols-[1fr_1fr_auto]">
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Product</span>
            <select value={productId} onChange={(e) => setProductId(e.target.value)} className={fieldClass}>
              <option value="">Select product</option>
              {productOptions.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.title}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Colour / size</span>
            <select
              disabled={!productId || productDetail.isLoading}
              defaultValue=""
              key={productId}
              onChange={(e) => {
                if (e.target.value) addVariant(e.target.value);
                e.target.value = "";
              }}
              className={`${fieldClass} disabled:opacity-50`}
            >
              <option value="">Add to bill</option>
              {variants.map((v) => (
                <option key={v.id} value={v.id}>
                  {[v.colorLabel, v.sizeLabel].filter(Boolean).join(" / ")} — {formatInr(v.price)} ({v.available} avail)
                </option>
              ))}
            </select>
          </label>
          <div className="flex items-end">
            <p className="pb-2 text-xs text-slate-500">
              <Plus size={12} className="mr-1 inline" />
              Only your store stock
            </p>
          </div>
        </div>

        {lines.length > 0 ? (
          <div className="overflow-x-auto rounded-xl border border-slate-200 dark:border-slate-700">
            <table className="w-full min-w-[560px] text-left text-sm">
              <thead className="bg-slate-50 text-xs text-slate-500 dark:bg-slate-800/80">
                <tr>
                  <th className="px-3 py-2">Item</th>
                  <th>Colour / Size</th>
                  <th className="text-right">Qty</th>
                  <th className="text-right">Unit price</th>
                  <th className="text-right">Line</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {lines.map((line) => (
                  <tr key={line.key} className="border-t border-slate-100 dark:border-slate-800">
                    <td className="px-3 py-2 font-medium">{line.productTitle}</td>
                    <td>{line.label}</td>
                    <td className="text-right">
                      <input
                        type="number"
                        min={1}
                        max={line.available}
                        value={line.quantity}
                        onChange={(e) =>
                          setLines((rows) =>
                            rows.map((r) => (r.key === line.key ? { ...r, quantity: e.target.value } : r)),
                          )
                        }
                        className="w-20 rounded-lg border px-2 py-1 text-right dark:border-slate-700 dark:bg-slate-900"
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
                            rows.map((r) => (r.key === line.key ? { ...r, unitPrice: e.target.value } : r)),
                          )
                        }
                        className="w-24 rounded-lg border px-2 py-1 text-right dark:border-slate-700 dark:bg-slate-900"
                      />
                    </td>
                    <td className="text-right tabular-nums">
                      {formatInr(Number(line.quantity || 0) * Number(line.unitPrice || 0))}
                    </td>
                    <td className="px-3 text-right">
                      <button
                        type="button"
                        aria-label="Remove line"
                        onClick={() => setLines((rows) => rows.filter((r) => r.key !== line.key))}
                        className="rounded p-1 text-slate-500 hover:bg-red-50 hover:text-red-700"
                      >
                        <Trash2 size={14} />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="rounded-xl border border-dashed border-slate-300 px-4 py-6 text-center text-sm text-slate-500 dark:border-slate-700">
            Add at least one colour/size from your published inventory.
          </p>
        )}
      </section>

      <section className="grid gap-4 sm:grid-cols-[1fr_280px]">
        <div className="space-y-3">
          <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-200">3. Tax & note</h3>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">GST % (you set for this bill)</span>
            <input
              required
              type="number"
              min={0}
              max={100}
              step="0.01"
              value={gstPercent}
              onChange={(e) => setGstPercent(e.target.value)}
              className={fieldClass}
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Internal note (optional)</span>
            <textarea
              rows={2}
              maxLength={1000}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              className={fieldClass}
            />
          </label>
        </div>
        <div className="rounded-xl bg-slate-50 px-4 py-3 text-sm dark:bg-slate-800/80">
          <h3 className="mb-2 text-sm font-semibold">Bill preview</h3>
          <div className="space-y-1.5">
            <div className="flex justify-between">
              <span>Subtotal</span>
              <span className="tabular-nums">{formatInr(totals.subtotal)}</span>
            </div>
            <div className="flex justify-between">
              <span>CGST ({totals.gst / 2}%)</span>
              <span className="tabular-nums">{formatInr(totals.cgst)}</span>
            </div>
            <div className="flex justify-between">
              <span>SGST ({totals.gst / 2}%)</span>
              <span className="tabular-nums">{formatInr(totals.sgst)}</span>
            </div>
            <div className="flex justify-between border-t border-slate-200 pt-2 font-semibold dark:border-slate-700">
              <span>Grand total</span>
              <span className="tabular-nums">{formatInr(totals.grandTotal)}</span>
            </div>
          </div>
        </div>
      </section>

      {create.isError && <p className="text-sm text-red-600">{apiErrorMessage(create.error)}</p>}

      <button
        type="submit"
        disabled={create.isPending || lines.length === 0}
        className="rounded-xl bg-teal-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50"
      >
        {create.isPending ? "Creating bill…" : "Create bill, deduct stock & save PDF"}
      </button>
    </form>
  );
}
