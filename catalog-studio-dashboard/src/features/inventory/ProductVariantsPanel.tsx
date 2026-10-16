import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, apiErrorMessage } from "../../api/client";

type VariantRow = {
  id: string;
  sku: string;
  sizeLabel?: string | null;
  colorLabel?: string | null;
  onHand: number;
  available: number;
};

export function ProductVariantsPanel({ productId }: { productId: string }) {
  const qc = useQueryClient();
  const [sku, setSku] = useState("");
  const [sizeLabel, setSizeLabel] = useState("");
  const [colorLabel, setColorLabel] = useState("");
  const [initialOnHand, setInitialOnHand] = useState("0");

  const variants = useQuery({
    queryKey: ["product-variants", productId],
    queryFn: async () => (await api.get(`/products/${productId}/variants`)).data.data as VariantRow[],
    enabled: Boolean(productId),
  });

  const create = useMutation({
    mutationFn: () =>
      api.post(`/products/${productId}/variants`, {
        sku,
        sizeLabel: sizeLabel || undefined,
        colorLabel: colorLabel || undefined,
        initialOnHand: Number(initialOnHand || 0),
      }),
    onSuccess: async () => {
      setSku("");
      setSizeLabel("");
      setColorLabel("");
      setInitialOnHand("0");
      await qc.invalidateQueries({ queryKey: ["product-variants", productId] });
      await qc.invalidateQueries({ queryKey: ["inventory"] });
    },
  });

  return (
    <section className="mt-6 space-y-3 rounded-2xl border border-slate-200 bg-white p-4">
      <h2 className="font-semibold">Variants & stock (Catalog Studio owns stock)</h2>
      <p className="text-xs text-slate-500">
        Example: same T-shirt as SKU TEE-RED-M (size M, color Red) with on-hand quantity. This number is the source of
        truth for v1.
      </p>
      <div className="grid gap-2 md:grid-cols-4">
        <input
          value={sku}
          onChange={(e) => setSku(e.target.value)}
          placeholder="SKU e.g. TEE-RED-M"
          className="rounded-lg border px-2 py-2 text-sm"
        />
        <input
          value={sizeLabel}
          onChange={(e) => setSizeLabel(e.target.value)}
          placeholder="Size"
          className="rounded-lg border px-2 py-2 text-sm"
        />
        <input
          value={colorLabel}
          onChange={(e) => setColorLabel(e.target.value)}
          placeholder="Color"
          className="rounded-lg border px-2 py-2 text-sm"
        />
        <input
          type="number"
          min={0}
          value={initialOnHand}
          onChange={(e) => setInitialOnHand(e.target.value)}
          placeholder="Initial stock"
          className="rounded-lg border px-2 py-2 text-sm"
        />
      </div>
      <button
        type="button"
        disabled={!sku.trim() || create.isPending}
        onClick={() => create.mutate()}
        className="rounded-xl bg-teal-700 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
      >
        {create.isPending ? "Saving…" : "Add variant"}
      </button>
      {create.isError && <p className="text-sm text-red-600">{apiErrorMessage(create.error)}</p>}
      <ul className="space-y-1 text-sm text-slate-700">
        {(variants.data || []).map((v) => (
          <li key={v.id} className="rounded-lg bg-slate-50 px-3 py-2">
            <strong>{v.sku}</strong> · {[v.sizeLabel, v.colorLabel].filter(Boolean).join(" / ") || "—"} · on hand{" "}
            {v.onHand} (available {v.available})
          </li>
        ))}
      </ul>
    </section>
  );
}
