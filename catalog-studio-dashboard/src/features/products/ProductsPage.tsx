import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useState } from "react";
import { api } from "../../api/client";
import type { Product } from "../../types";

export function ProductsPage() {
  const [view, setView] = useState<"grid" | "table">("grid");
  const [q, setQ] = useState("");
  const { data } = useQuery({
    queryKey: ["products", q],
    queryFn: async () => (await api.get("/products", { params: { q } })).data.data,
  });
  const products: Product[] = data?.content || [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">My products</h1>
          <p className="text-slate-500">Saved listings ready for marketplace autofill.</p>
        </div>
        <Link to="/products/analyze" className="rounded-xl bg-teal-700 px-4 py-2 text-white">
          Analyze product
        </Link>
      </div>
      <div className="flex flex-wrap gap-3">
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search" className="rounded-xl border border-slate-200 px-3 py-2" />
        <button onClick={() => setView("grid")} className="rounded-xl border px-3 py-2 text-sm">
          Grid
        </button>
        <button onClick={() => setView("table")} className="rounded-xl border px-3 py-2 text-sm">
          Table
        </button>
      </div>
      {products.length === 0 && <Empty text="No products yet. Analyze your first catalog photos." />}
      {view === "grid" ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {products.map((p) => (
            <article key={p.id} className="overflow-hidden rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900">
              <div className="h-40 bg-slate-100">
                {p.images?.[0]?.url && <img src={p.images[0].url} alt="" className="h-40 w-full object-cover" />}
              </div>
              <div className="space-y-1 p-4">
                <h3 className="font-medium">{p.name || "Untitled"}</h3>
                <p className="text-sm text-slate-500">
                  {p.productType} · {p.primaryColor}
                </p>
                <p className="text-xs uppercase text-slate-400">{p.status}</p>
                <div className="flex gap-2 pt-2 text-sm">
                  <Link to={`/products/${p.id}/review`} className="text-teal-700">
                    Edit
                  </Link>
                  <Link to="/marketplaces" className="text-slate-600">
                    Create listing
                  </Link>
                </div>
              </div>
            </article>
          ))}
        </div>
      ) : (
        <table className="w-full overflow-hidden rounded-2xl bg-white text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="p-3">Product</th>
              <th>Type</th>
              <th>Color</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {products.map((p) => (
              <tr key={p.id} className="border-t">
                <td className="p-3">{p.name}</td>
                <td>{p.productType}</td>
                <td>{p.primaryColor}</td>
                <td>{p.status}</td>
                <td>
                  <Link to={`/products/${p.id}/review`} className="text-teal-700">
                    View
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export function Empty({ text }: { text: string }) {
  return <div className="rounded-2xl border border-dashed border-slate-300 p-10 text-center text-slate-500">{text}</div>;
}
