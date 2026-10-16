import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { ChevronDown, ChevronUp, Plus, Trash2, Upload } from "lucide-react";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import {
  formatInr,
  priceRange,
  type PageResult,
  type ProductCard,
  type ProductDetail,
  type WholesaleCategory,
} from "./wholesale";

const inputClass =
  "w-full rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-sm outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100 dark:border-slate-700 dark:bg-slate-900";

type VariantRow = { colorLabel: string; colorHex: string; sizeLabel: string; price: string; mrp: string; stock: string };

const blankVariant = (): VariantRow => ({ colorLabel: "", colorHex: "", sizeLabel: "", price: "", mrp: "", stock: "0" });

const STATUS_STYLE: Record<string, string> = {
  ACTIVE: "bg-teal-100 text-teal-800",
  DRAFT: "bg-amber-100 text-amber-800",
  ARCHIVED: "bg-slate-200 text-slate-700",
};

export function SupplierInventoryPage() {
  const qc = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [page, setPage] = useState(0);
  const [expanded, setExpanded] = useState<string | null>(null);

  const products = useQuery({
    queryKey: ["ws-my-products", page],
    queryFn: async () =>
      (await api.get("/wholesale/supplier/products", { params: { page, size: 20 } })).data.data as PageResult<ProductCard>,
    retry: false,
  });

  const publish = useMutation({
    mutationFn: (id: string) => api.post(`/wholesale/supplier/products/${id}/publish`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ws-my-products"] }),
  });
  const archive = useMutation({
    mutationFn: (id: string) => api.delete(`/wholesale/supplier/products/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ws-my-products"] }),
  });

  const status = axios.isAxiosError(products.error) ? products.error.response?.status : undefined;
  const items = products.data?.items || [];

  if (status === 403 || status === 404) {
    return (
      <div className="mx-auto max-w-2xl space-y-3 rounded-2xl border border-amber-200 bg-amber-50 p-6 text-sm text-amber-950">
        <p className="text-base font-semibold">{status === 403 ? "Supplier plan required" : "Create your store first"}</p>
        <p>{apiErrorMessage(products.error)}</p>
        <Link to={status === 403 ? "/subscription" : "/supplier/store"} className="inline-block rounded-xl bg-teal-700 px-4 py-2 font-semibold text-white">
          {status === 403 ? "View plans" : "Open my store"}
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Supplier inventory</h1>
          <p className="text-sm text-slate-500">Add products with colour and size variants, upload photos, then publish to Live Stock.</p>
        </div>
        <button
          type="button"
          onClick={() => setShowForm((v) => !v)}
          className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-800"
        >
          <Plus size={16} /> {showForm ? "Close form" : "Add product"}
        </button>
      </div>

      {showForm && (
        <ProductForm
          onDone={async () => {
            setShowForm(false);
            await qc.invalidateQueries({ queryKey: ["ws-my-products"] });
          }}
        />
      )}

      {products.isLoading && <p className="text-sm text-slate-500">Loading products...</p>}
      {products.isError && <p className="text-sm text-red-600">{apiErrorMessage(products.error)}</p>}
      {(publish.isError || archive.isError) && (
        <p className="text-sm text-red-600">{apiErrorMessage(publish.error || archive.error)}</p>
      )}

      {!products.isLoading && items.length === 0 && !showForm && (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900">
          No products yet. Click <strong>Add product</strong> to list your first item.
        </div>
      )}

      <div className="space-y-3">
        {items.map((product) => (
          <div key={product.id} className="rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
            <div className="flex flex-wrap items-center gap-3 p-3">
              <div className="h-16 w-14 shrink-0 overflow-hidden rounded-lg bg-slate-100 dark:bg-slate-800">
                {product.primaryImageUrl && <img src={product.primaryImageUrl} alt="" className="h-full w-full object-cover" />}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate font-semibold">{product.title}</p>
                <p className="text-xs text-slate-500">
                  {product.code} - {product.categoryName} - {priceRange(product.minPrice, product.maxPrice)} - {product.totalStock} pcs -{" "}
                  {product.bookedCount} booked
                </p>
              </div>
              <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${STATUS_STYLE[product.status] || "bg-slate-100"}`}>
                {product.status}
              </span>
              <div className="flex items-center gap-2">
                {product.status !== "ACTIVE" && (
                  <button
                    type="button"
                    disabled={publish.isPending}
                    onClick={() => publish.mutate(product.id)}
                    className="rounded-lg bg-teal-700 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
                  >
                    Publish
                  </button>
                )}
                {product.status !== "ARCHIVED" && (
                  <button
                    type="button"
                    disabled={archive.isPending}
                    onClick={() => {
                      if (window.confirm("Archive this product? It will disappear from Live Stock.")) archive.mutate(product.id);
                    }}
                    className="rounded-lg border px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
                  >
                    Archive
                  </button>
                )}
                <button
                  type="button"
                  aria-label="Toggle stock details"
                  onClick={() => setExpanded((current) => (current === product.id ? null : product.id))}
                  className="rounded-lg border p-1.5 hover:bg-slate-50"
                >
                  {expanded === product.id ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                </button>
              </div>
            </div>
            {expanded === product.id && <StockPanel productId={product.id} />}
          </div>
        ))}
      </div>

      {products.data && products.data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <button type="button" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))} className="rounded-lg border px-3 py-1.5 disabled:opacity-40">
            Previous
          </button>
          <span className="text-slate-500">
            Page {products.data.page + 1} of {products.data.totalPages}
          </span>
          <button type="button" disabled={!products.data.hasNext} onClick={() => setPage((p) => p + 1)} className="rounded-lg border px-3 py-1.5 disabled:opacity-40">
            Next
          </button>
        </div>
      )}
    </div>
  );
}

function StockPanel({ productId }: { productId: string }) {
  const qc = useQueryClient();
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [photoMessage, setPhotoMessage] = useState("");

  const detail = useQuery({
    queryKey: ["ws-my-product", productId],
    queryFn: async () => (await api.get(`/wholesale/supplier/products/${productId}`)).data.data as ProductDetail,
  });

  const adjust = useMutation({
    mutationFn: (payload: { variantId: string; onHand: number }) =>
      api.post(`/wholesale/supplier/variants/${payload.variantId}/stock`, { onHand: payload.onHand }),
    onSuccess: async () => {
      setDrafts({});
      await qc.invalidateQueries({ queryKey: ["ws-my-product", productId] });
      await qc.invalidateQueries({ queryKey: ["ws-my-products"] });
    },
  });

  const addPhoto = useMutation({
    mutationFn: async (files: File[]) => {
      for (const file of files) {
        const body = new FormData();
        body.append("file", file);
        await api.post(`/wholesale/supplier/products/${productId}/images`, body);
      }
    },
    onSuccess: async () => {
      setPhotoMessage("Photos uploaded");
      await qc.invalidateQueries({ queryKey: ["ws-my-product", productId] });
      await qc.invalidateQueries({ queryKey: ["ws-my-products"] });
    },
    onError: () => setPhotoMessage(""),
  });

  if (detail.isLoading) return <p className="border-t px-4 py-3 text-sm text-slate-500">Loading variants...</p>;
  if (detail.isError || !detail.data) return <p className="border-t px-4 py-3 text-sm text-red-600">{apiErrorMessage(detail.error)}</p>;
  const data = detail.data;

  return (
    <div className="space-y-3 border-t border-slate-100 p-4 dark:border-slate-800">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[560px] text-left text-sm">
          <thead className="text-xs text-slate-500">
            <tr>
              <th className="py-1">SKU</th>
              <th>Colour / Size</th>
              <th>Price</th>
              <th>On hand</th>
              <th>Reserved</th>
              <th>Available</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {data.variants.map((variant) => {
              const draft = drafts[variant.id] ?? String(variant.onHand ?? 0);
              return (
                <tr key={variant.id} className="border-t border-slate-100 dark:border-slate-800">
                  <td className="py-1.5 font-mono text-xs">{variant.sku}</td>
                  <td>
                    {variant.colorLabel} / {variant.sizeLabel}
                  </td>
                  <td>{formatInr(variant.price)}</td>
                  <td>
                    <input
                      type="number"
                      min={variant.reserved ?? 0}
                      value={draft}
                      onChange={(e) => setDrafts((d) => ({ ...d, [variant.id]: e.target.value }))}
                      className="w-24 rounded-lg border px-2 py-1 dark:border-slate-700 dark:bg-slate-900"
                    />
                  </td>
                  <td>{variant.reserved ?? 0}</td>
                  <td>{variant.available}</td>
                  <td className="text-right">
                    <button
                      type="button"
                      disabled={adjust.isPending || Number(draft) === variant.onHand || draft === ""}
                      onClick={() => adjust.mutate({ variantId: variant.id, onHand: Number(draft) })}
                      className="rounded-lg bg-teal-700 px-3 py-1 text-xs font-semibold text-white disabled:opacity-40"
                    >
                      Save
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {adjust.isError && <p className="text-sm text-red-600">{apiErrorMessage(adjust.error)}</p>}

      <div className="flex flex-wrap items-center gap-3 text-sm">
        <span className="text-slate-500">{data.images.length} photo(s)</span>
        <label className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-teal-200 px-3 py-1.5 text-xs font-semibold text-teal-800 hover:bg-teal-50">
          <Upload size={14} /> Add photos
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            className="hidden"
            onChange={(e) => {
              const files = Array.from(e.target.files || []);
              e.target.value = "";
              if (files.length) addPhoto.mutate(files);
            }}
          />
        </label>
        {addPhoto.isPending && <span className="text-slate-500">Uploading...</span>}
        {photoMessage && <span className="text-teal-800">{photoMessage}</span>}
        {addPhoto.isError && <span className="text-red-600">{apiErrorMessage(addPhoto.error)}</span>}
      </div>
    </div>
  );
}

function ProductForm({ onDone }: { onDone: () => void | Promise<void> }) {
  const [title, setTitle] = useState("");
  const [code, setCode] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [mrp, setMrp] = useState("");
  const [description, setDescription] = useState("");
  const [details, setDetails] = useState("");
  const [variants, setVariants] = useState<VariantRow[]>([blankVariant()]);
  const [files, setFiles] = useState<File[]>([]);
  const [publishNow, setPublishNow] = useState(true);
  const [progress, setProgress] = useState("");
  const [bulk, setBulk] = useState({ colorLabel: "", colorHex: "", sizes: "S, M, L, XL", price: "", stock: "10" });

  const categories = useQuery({
    queryKey: ["ws-categories"],
    queryFn: async () => (await api.get("/wholesale/categories")).data.data as WholesaleCategory[],
    staleTime: 60_000,
  });

  const create = useMutation({
    mutationFn: async () => {
      setProgress("Creating product...");
      const created = (
        await api.post("/wholesale/supplier/products", {
          title: title.trim(),
          code: code.trim(),
          categoryId,
          description: description.trim() || undefined,
          details: details.trim() || undefined,
          mrp: mrp ? Number(mrp) : undefined,
          variants: variants.map((v) => ({
            colorLabel: v.colorLabel.trim() || undefined,
            colorHex: v.colorHex || undefined,
            sizeLabel: v.sizeLabel.trim() || undefined,
            price: Number(v.price),
            mrp: v.mrp ? Number(v.mrp) : undefined,
            stock: Number(v.stock) || 0,
          })),
        })
      ).data.data as ProductDetail;

      for (let index = 0; index < files.length; index += 1) {
        setProgress(`Uploading photo ${index + 1} of ${files.length}...`);
        const body = new FormData();
        body.append("file", files[index]);
        await api.post(`/wholesale/supplier/products/${created.id}/images`, body);
      }
      if (publishNow && files.length > 0) {
        setProgress("Publishing...");
        await api.post(`/wholesale/supplier/products/${created.id}/publish`);
      }
      return created;
    },
    onSuccess: async () => {
      setProgress("");
      await onDone();
    },
    onError: () => setProgress(""),
  });

  function setVariant(index: number, patch: Partial<VariantRow>) {
    setVariants((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  }

  function addBulkSizes() {
    const sizes = bulk.sizes
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    if (sizes.length === 0 || !bulk.price) return;
    const rows = sizes.map<VariantRow>((size) => ({
      colorLabel: bulk.colorLabel,
      colorHex: bulk.colorHex,
      sizeLabel: size,
      price: bulk.price,
      mrp: "",
      stock: bulk.stock,
    }));
    setVariants((current) => {
      const onlyBlank = current.length === 1 && !current[0].price && !current[0].colorLabel && !current[0].sizeLabel;
      return onlyBlank ? rows : [...current, ...rows];
    });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate();
  }

  const valid =
    title.trim() && code.trim() && categoryId && variants.length > 0 && variants.every((v) => v.price !== "" && Number(v.price) >= 0);

  return (
    <form onSubmit={submit} className="space-y-4 rounded-2xl border border-teal-200 bg-white p-5 dark:border-teal-900 dark:bg-slate-900">
      <h2 className="text-lg font-semibold">New product</h2>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="space-y-1 text-sm">
          <span className="font-medium">Title</span>
          <input required maxLength={255} value={title} onChange={(e) => setTitle(e.target.value)} className={inputClass} />
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium">Product code</span>
          <input required maxLength={60} value={code} onChange={(e) => setCode(e.target.value)} placeholder="KT-101" className={inputClass} />
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium">Category</span>
          <select required value={categoryId} onChange={(e) => setCategoryId(e.target.value)} className={inputClass}>
            <option value="">Select category</option>
            {(categories.data || []).map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </label>
        <label className="space-y-1 text-sm">
          <span className="font-medium">MRP (optional)</span>
          <input type="number" min={0} step="0.01" value={mrp} onChange={(e) => setMrp(e.target.value)} className={inputClass} />
        </label>
      </div>
      <label className="block space-y-1 text-sm">
        <span className="font-medium">Description</span>
        <textarea rows={2} maxLength={8000} value={description} onChange={(e) => setDescription(e.target.value)} className={inputClass} />
      </label>
      <label className="block space-y-1 text-sm">
        <span className="font-medium">Product details (fabric, pack, care, etc.)</span>
        <textarea rows={3} maxLength={8000} value={details} onChange={(e) => setDetails(e.target.value)} className={inputClass} />
      </label>

      <div className="space-y-2">
        <p className="text-sm font-medium">Colour and size variants</p>
        <div className="grid items-end gap-2 rounded-xl bg-teal-50/70 p-3 text-sm dark:bg-teal-950/30 sm:grid-cols-6">
          <input placeholder="Colour" value={bulk.colorLabel} onChange={(e) => setBulk({ ...bulk, colorLabel: e.target.value })} className={inputClass} />
          <input
            type="color"
            aria-label="Colour swatch"
            value={bulk.colorHex || "#cccccc"}
            onChange={(e) => setBulk({ ...bulk, colorHex: e.target.value })}
            className="h-9 w-full rounded-lg border border-slate-200"
          />
          <input placeholder="Sizes: S, M, L" value={bulk.sizes} onChange={(e) => setBulk({ ...bulk, sizes: e.target.value })} className={`${inputClass} sm:col-span-2`} />
          <input type="number" min={0} placeholder="Price" value={bulk.price} onChange={(e) => setBulk({ ...bulk, price: e.target.value })} className={inputClass} />
          <div className="flex gap-2">
            <input type="number" min={0} placeholder="Stock" value={bulk.stock} onChange={(e) => setBulk({ ...bulk, stock: e.target.value })} className={inputClass} />
            <button type="button" onClick={addBulkSizes} className="shrink-0 rounded-lg bg-teal-700 px-3 text-xs font-semibold text-white">
              Add
            </button>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="text-xs text-slate-500">
              <tr>
                <th className="py-1">Colour</th>
                <th>Swatch</th>
                <th>Size</th>
                <th>Price</th>
                <th>MRP</th>
                <th>Stock</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {variants.map((row, index) => (
                <tr key={index} className="border-t border-slate-100 dark:border-slate-800">
                  <td className="py-1 pr-2">
                    <input value={row.colorLabel} onChange={(e) => setVariant(index, { colorLabel: e.target.value })} placeholder="Default" className={inputClass} />
                  </td>
                  <td className="pr-2">
                    <input
                      type="color"
                      aria-label="Swatch"
                      value={row.colorHex || "#cccccc"}
                      onChange={(e) => setVariant(index, { colorHex: e.target.value })}
                      className="h-8 w-12 rounded border border-slate-200"
                    />
                  </td>
                  <td className="pr-2">
                    <input value={row.sizeLabel} onChange={(e) => setVariant(index, { sizeLabel: e.target.value })} placeholder="Free" className={inputClass} />
                  </td>
                  <td className="pr-2">
                    <input required type="number" min={0} step="0.01" value={row.price} onChange={(e) => setVariant(index, { price: e.target.value })} className={inputClass} />
                  </td>
                  <td className="pr-2">
                    <input type="number" min={0} step="0.01" value={row.mrp} onChange={(e) => setVariant(index, { mrp: e.target.value })} className={inputClass} />
                  </td>
                  <td className="pr-2">
                    <input required type="number" min={0} value={row.stock} onChange={(e) => setVariant(index, { stock: e.target.value })} className={inputClass} />
                  </td>
                  <td>
                    <button
                      type="button"
                      aria-label="Remove variant"
                      disabled={variants.length === 1}
                      onClick={() => setVariants((rows) => rows.filter((_, i) => i !== index))}
                      className="rounded-lg p-1.5 text-slate-500 hover:bg-red-50 hover:text-red-600 disabled:opacity-30"
                    >
                      <Trash2 size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <button type="button" onClick={() => setVariants((rows) => [...rows, blankVariant()])} className="text-sm font-medium text-teal-700 hover:underline">
          + Add variant row
        </button>
      </div>

      <div className="space-y-2">
        <p className="text-sm font-medium">Photos (up to 10)</p>
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          onChange={(e) => setFiles(Array.from(e.target.files || []).slice(0, 10))}
          className="block w-full text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-teal-700 file:px-3 file:py-1.5 file:text-sm file:font-semibold file:text-white"
        />
        {files.length > 0 && (
          <div className="flex flex-wrap gap-2">
            {files.map((file) => (
              <img key={file.name + file.size} src={URL.createObjectURL(file)} alt="" className="h-16 w-14 rounded-lg object-cover" />
            ))}
          </div>
        )}
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={publishNow} onChange={(e) => setPublishNow(e.target.checked)} />
          Publish to Live Stock right away (needs at least one photo)
        </label>
      </div>

      {create.isError && (
        <p className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
          {apiErrorMessage(create.error)}
        </p>
      )}
      <div className="flex items-center gap-3">
        <button
          type="submit"
          disabled={!valid || create.isPending}
          className="rounded-xl bg-teal-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50"
        >
          {create.isPending ? "Saving..." : "Save product"}
        </button>
        {progress && <span className="text-sm text-slate-500">{progress}</span>}
      </div>
    </form>
  );
}
