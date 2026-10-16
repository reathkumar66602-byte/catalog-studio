import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Minus, Plus } from "lucide-react";
import { useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import { formatInr, priceRange, type ProductDetail, type VariantCell } from "./wholesale";

export function WholesaleProductDetailPage() {
  const { idOrSlug = "" } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [color, setColor] = useState<string>("");
  const [imageIndex, setImageIndex] = useState(0);
  const [qty, setQty] = useState<Record<string, number>>({});
  const [note, setNote] = useState("");

  const product = useQuery({
    queryKey: ["ws-product", idOrSlug],
    queryFn: async () => (await api.get(`/wholesale/products/${encodeURIComponent(idOrSlug)}`)).data.data as ProductDetail,
  });

  const data = product.data;
  const activeColor = color || data?.colors[0]?.label || "";
  const isOwner = Boolean(data?.variants.some((v) => v.onHand !== null && v.onHand !== undefined));

  const variantsByColor = useMemo(() => {
    const map = new Map<string, VariantCell[]>();
    if (!data) return map;
    const order = new Map(data.sizes.map((size, index) => [size, index]));
    for (const variant of data.variants) {
      if (variant.status !== "ACTIVE") continue;
      map.set(variant.colorLabel, [...(map.get(variant.colorLabel) || []), variant]);
    }
    map.forEach((list) => list.sort((a, b) => (order.get(a.sizeLabel) ?? 0) - (order.get(b.sizeLabel) ?? 0)));
    return map;
  }, [data]);

  const lines = useMemo(() => {
    if (!data) return [];
    return data.variants
      .filter((v) => (qty[v.id] || 0) > 0)
      .map((v) => ({ variant: v, quantity: qty[v.id] }));
  }, [data, qty]);
  const totalPcs = lines.reduce((sum, line) => sum + line.quantity, 0);
  const totalAmount = lines.reduce((sum, line) => sum + line.quantity * line.variant.price, 0);

  const place = useMutation({
    mutationFn: async () =>
      (
        await api.post("/wholesale/buyer/orders", {
          items: lines.map((line) => ({ variantId: line.variant.id, quantity: line.quantity })),
          note: note.trim() || undefined,
        })
      ).data,
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["ws-products"] });
      await qc.invalidateQueries({ queryKey: ["ws-buyer-orders"] });
      navigate("/live-stock/orders");
    },
    onError: () => {
      void qc.invalidateQueries({ queryKey: ["ws-product", idOrSlug] });
    },
  });

  function setVariantQty(variant: VariantCell, next: number) {
    const clamped = Math.max(0, Math.min(next, variant.available));
    setQty((current) => ({ ...current, [variant.id]: clamped }));
  }

  function addFullSet() {
    const list = variantsByColor.get(activeColor) || [];
    setQty((current) => {
      const next = { ...current };
      for (const variant of list) {
        if (variant.available > 0) {
          next[variant.id] = Math.min((next[variant.id] || 0) + 1, variant.available);
        }
      }
      return next;
    });
  }

  if (product.isLoading) return <p className="text-sm text-slate-500">Loading product...</p>;
  if (product.isError || !data) {
    return (
      <div className="space-y-3">
        <Link to="/live-stock" className="inline-flex items-center gap-1 text-sm text-teal-700">
          <ArrowLeft size={16} /> Back to Live Stock
        </Link>
        <p className="text-sm text-red-600">{apiErrorMessage(product.error, "Product not found")}</p>
      </div>
    );
  }

  const colorImages = data.images.filter((image) => image.colorLabel && image.colorLabel === activeColor);
  const gallery = colorImages.length > 0 ? colorImages : data.images;
  const mainImage = gallery[Math.min(imageIndex, Math.max(gallery.length - 1, 0))];
  const colorVariants = variantsByColor.get(activeColor) || [];
  const showMrp = data.mrp && data.minPrice && data.mrp > data.minPrice;

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <Link to="/live-stock" className="inline-flex items-center gap-1 text-sm font-medium text-teal-700 hover:underline">
        <ArrowLeft size={16} /> Back to Live Stock
      </Link>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,420px)_1fr]">
        <div className="space-y-3">
          <div className="aspect-[3/4] overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 dark:border-slate-800 dark:bg-slate-800">
            {mainImage ? (
              <img src={mainImage.url} alt={data.title} className="h-full w-full object-cover" />
            ) : (
              <div className="flex h-full items-center justify-center text-sm text-slate-400">No photo</div>
            )}
          </div>
          {gallery.length > 1 && (
            <div className="flex gap-2 overflow-x-auto">
              {gallery.map((image, index) => (
                <button
                  key={image.id}
                  type="button"
                  onClick={() => setImageIndex(index)}
                  className={`h-16 w-14 shrink-0 overflow-hidden rounded-lg border-2 ${
                    index === imageIndex ? "border-teal-600" : "border-transparent"
                  }`}
                >
                  <img src={image.url} alt="" className="h-full w-full object-cover" />
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="space-y-5">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-teal-700">{data.category.name}</p>
            <h1 className="mt-1 text-2xl font-semibold">{data.title}</h1>
            <p className="mt-1 text-sm text-slate-500">
              Code: {data.code} - Sold by{" "}
              <span className="font-medium text-slate-700 dark:text-slate-200">{data.store.name}</span>
              {data.store.city ? `, ${data.store.city}` : ""}
            </p>
            <p className="mt-3 flex flex-wrap items-baseline gap-3">
              <span className="text-3xl font-bold text-teal-800 dark:text-teal-300">{priceRange(data.minPrice, data.maxPrice)}</span>
              {showMrp && <span className="text-sm text-slate-400 line-through">{formatInr(data.mrp)}</span>}
              <span className="rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700">
                {data.totalStock} pcs in stock
              </span>
              {data.bookedCount > 0 && (
                <span className="rounded-full bg-teal-700 px-2.5 py-0.5 text-xs font-semibold text-white">
                  {data.bookedCount} booked
                </span>
              )}
            </p>
          </div>

          <div>
            <p className="mb-2 text-sm font-medium">Colour: {activeColor}</p>
            <div className="flex flex-wrap gap-2">
              {data.colors.map((swatch) => (
                <button
                  key={swatch.label}
                  type="button"
                  onClick={() => {
                    setColor(swatch.label);
                    setImageIndex(0);
                  }}
                  className={`flex items-center gap-2 rounded-full border px-3 py-1.5 text-sm ${
                    swatch.label === activeColor
                      ? "border-teal-700 bg-teal-50 font-semibold text-teal-900"
                      : "border-slate-200 bg-white hover:border-teal-300 dark:border-slate-700 dark:bg-slate-900"
                  }`}
                >
                  <span
                    className="h-4 w-4 rounded-full border border-slate-300"
                    style={{ backgroundColor: swatch.hex || undefined }}
                  />
                  {swatch.label}
                </button>
              ))}
            </div>
          </div>

          <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
            <table className="w-full text-center text-sm">
              <caption className="px-4 pt-3 text-left text-sm font-semibold">Stock by colour and size (pcs)</caption>
              <thead className="text-xs text-slate-500">
                <tr>
                  <th className="px-4 py-2 text-left">Colour</th>
                  {data.sizes.map((size) => (
                    <th key={size} className="px-3 py-2">
                      {size}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {data.colors.map((swatch) => (
                  <tr
                    key={swatch.label}
                    className={`border-t border-slate-100 dark:border-slate-800 ${swatch.label === activeColor ? "bg-teal-50/60 dark:bg-teal-900/20" : ""}`}
                  >
                    <td className="px-4 py-2 text-left font-medium">{swatch.label}</td>
                    {data.sizes.map((size) => {
                      const stock = data.stockMatrix[swatch.label]?.[size];
                      return (
                        <td
                          key={size}
                          className={`px-3 py-2 ${stock === undefined ? "text-slate-300" : stock === 0 ? "text-red-500" : "font-semibold text-emerald-700"}`}
                        >
                          {stock === undefined ? "-" : stock}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm font-semibold">Select quantity - {activeColor}</p>
              <button
                type="button"
                onClick={addFullSet}
                disabled={isOwner || colorVariants.every((v) => v.available <= 0)}
                className="rounded-lg border border-teal-700 px-3 py-1.5 text-xs font-semibold text-teal-800 hover:bg-teal-50 disabled:opacity-40"
              >
                +1 Full Set
              </button>
            </div>
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              {colorVariants.map((variant) => {
                const value = qty[variant.id] || 0;
                return (
                  <div key={variant.id} className="flex items-center justify-between gap-3 py-2.5">
                    <div>
                      <p className="text-sm font-medium">Size {variant.sizeLabel}</p>
                      <p className="text-xs text-slate-500">
                        {formatInr(variant.price)} - {variant.available > 0 ? `${variant.available} pcs` : "Out of stock"}
                      </p>
                    </div>
                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        aria-label={`Decrease ${variant.sizeLabel}`}
                        disabled={value <= 0}
                        onClick={() => setVariantQty(variant, value - 1)}
                        className="rounded-lg border p-1.5 disabled:opacity-30"
                      >
                        <Minus size={14} />
                      </button>
                      <input
                        type="number"
                        min={0}
                        max={variant.available}
                        value={value}
                        disabled={isOwner || variant.available <= 0}
                        onChange={(e) => setVariantQty(variant, Number(e.target.value) || 0)}
                        className="w-16 rounded-lg border px-2 py-1 text-center text-sm dark:border-slate-700 dark:bg-slate-900"
                      />
                      <button
                        type="button"
                        aria-label={`Increase ${variant.sizeLabel}`}
                        disabled={isOwner || value >= variant.available}
                        onClick={() => setVariantQty(variant, value + 1)}
                        className="rounded-lg border p-1.5 disabled:opacity-30"
                      >
                        <Plus size={14} />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {isOwner ? (
            <p className="rounded-xl bg-slate-100 px-4 py-3 text-sm text-slate-600">
              This is your own product. Booking is available to other buyers.
            </p>
          ) : (
            <div className="space-y-3 rounded-2xl border border-teal-200 bg-teal-50/60 p-4 dark:border-teal-900 dark:bg-teal-950/30">
              {lines.length > 0 && (
                <ul className="space-y-1 text-sm">
                  {lines.map(({ variant, quantity }) => (
                    <li key={variant.id} className="flex justify-between gap-3">
                      <span>
                        {variant.colorLabel} / {variant.sizeLabel} x {quantity}
                      </span>
                      <span className="font-medium">{formatInr(variant.price * quantity)}</span>
                    </li>
                  ))}
                </ul>
              )}
              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                rows={2}
                maxLength={1000}
                placeholder="Note for the supplier (optional)"
                className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-teal-600 dark:border-slate-700 dark:bg-slate-900"
              />
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm">
                  <span className="font-semibold">{totalPcs}</span> pcs -{" "}
                  <span className="text-lg font-bold text-teal-800">{formatInr(totalAmount)}</span>
                </p>
                <button
                  type="button"
                  disabled={totalPcs === 0 || place.isPending}
                  onClick={() => place.mutate()}
                  className="rounded-xl bg-teal-700 px-6 py-2.5 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50"
                >
                  {place.isPending ? "Placing order..." : "Place order"}
                </button>
              </div>
              {place.isError && <p className="text-sm text-red-600">{apiErrorMessage(place.error)}</p>}
              <p className="text-xs text-slate-500">
                Stock is reserved when you book. Supplier contact details unlock after the supplier accepts your order.
              </p>
            </div>
          )}
        </div>
      </div>

      {(data.description || data.details) && (
        <div className="grid gap-4 md:grid-cols-2">
          {data.description && (
            <section className="rounded-2xl border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
              <h2 className="mb-1 text-sm font-semibold">Description</h2>
              <p className="whitespace-pre-line text-sm text-slate-600 dark:text-slate-300">{data.description}</p>
            </section>
          )}
          {data.details && (
            <section className="rounded-2xl border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-900">
              <h2 className="mb-1 text-sm font-semibold">Product details</h2>
              <p className="whitespace-pre-line text-sm text-slate-600 dark:text-slate-300">{data.details}</p>
            </section>
          )}
        </div>
      )}
    </div>
  );
}
