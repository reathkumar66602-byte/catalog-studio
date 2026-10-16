import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";

type VariantRow = {
  id: string;
  productId: string;
  productName?: string | null;
  sku: string;
  sizeLabel?: string | null;
  colorLabel?: string | null;
  onHand: number;
  reserved: number;
  available: number;
};

export function InventoryPage() {
  const qc = useQueryClient();
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [message, setMessage] = useState("");

  const inventory = useQuery({
    queryKey: ["inventory"],
    queryFn: async () => (await api.get("/inventory")).data.data as VariantRow[],
  });

  const adjust = useMutation({
    mutationFn: (payload: { variantId: string; onHand: number }) =>
      api.post("/inventory/adjust", { ...payload, note: "Manual adjust from Inventory page" }),
    onSuccess: async () => {
      setMessage("Stock updated in Catalog Studio. Flipkart push will use this number once seller API sync is enabled.");
      await qc.invalidateQueries({ queryKey: ["inventory"] });
    },
  });

  const rows = inventory.data || [];

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-4 md:p-6">
      <div>
        <h1 className="text-2xl font-semibold">Inventory</h1>
        <p className="mt-1 text-sm text-slate-500">
          Catalog Studio owns stock for v1. Change <strong>On hand</strong> here; use Marketplaces → Meesho{" "}
          <strong>Push stock</strong> after connecting Supplier Panel login
          from this number. Create variants from a{" "}
          <Link to="/products" className="text-teal-700 underline">
            product
          </Link>
          .
        </p>
      </div>

      {inventory.isLoading && <p className="text-sm text-slate-500">Loading inventory…</p>}
      {inventory.isError && <p className="text-sm text-red-600">{apiErrorMessage(inventory.error)}</p>}
      {message && <p className="text-sm text-teal-800">{message}</p>}
      {adjust.isError && <p className="text-sm text-red-600">{apiErrorMessage(adjust.error)}</p>}

      {rows.length === 0 && !inventory.isLoading ? (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-white px-6 py-10 text-center text-sm text-slate-500">
          No variants yet. Open a product, add a SKU variant (size/color), and set initial stock.
        </div>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                <th className="px-4 py-3">SKU</th>
                <th>Product</th>
                <th>Size / Color</th>
                <th>On hand</th>
                <th>Reserved</th>
                <th>Available</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const draft = drafts[row.id] ?? String(row.onHand);
                return (
                  <tr key={row.id} className="border-t">
                    <td className="px-4 py-3 font-medium">{row.sku}</td>
                    <td>{row.productName || "—"}</td>
                    <td>
                      {[row.sizeLabel, row.colorLabel].filter(Boolean).join(" / ") || "—"}
                    </td>
                    <td>
                      <input
                        type="number"
                        min={0}
                        value={draft}
                        onChange={(e) => setDrafts((d) => ({ ...d, [row.id]: e.target.value }))}
                        className="w-24 rounded-lg border px-2 py-1"
                      />
                    </td>
                    <td>{row.reserved}</td>
                    <td>{row.available}</td>
                    <td className="px-4 py-3 text-right">
                      <button
                        type="button"
                        disabled={adjust.isPending || Number(draft) === row.onHand}
                        onClick={() => {
                          setMessage("");
                          adjust.mutate({ variantId: row.id, onHand: Number(draft) });
                        }}
                        className="rounded-lg bg-teal-700 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
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
      )}
    </div>
  );
}
