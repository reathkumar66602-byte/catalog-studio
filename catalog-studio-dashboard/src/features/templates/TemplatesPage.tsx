import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { api } from "../../api/client";
import { Empty } from "../products/ProductsPage";

export function TemplatesPage() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ["templates"], queryFn: async () => (await api.get("/templates")).data.data });
  const [open, setOpen] = useState(false);
  const create = useMutation({
    mutationFn: (body: object) => api.post("/templates", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["templates"] });
      setOpen(false);
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.delete(`/templates/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["templates"] }),
  });
  const items = data?.content || [];

  return (
    <CrudScreen
      title="Listing templates"
      subtitle="Reuse fabric, sleeve, and fit defaults across catalog items."
      actionLabel="Create template"
      onAction={() => setOpen(true)}
    >
      {items.length === 0 && <Empty text="No templates yet. Create a Men's Shirt or Women's Kurti template." />}
      <div className="grid gap-4 md:grid-cols-3">
        {items.map((item: { id: string; name: string; marketplace: string; productType?: string }) => (
          <article key={item.id} className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
            <h3 className="font-medium">{item.name}</h3>
            <p className="text-sm text-slate-500">
              {item.marketplace} · {item.productType}
            </p>
            <button className="mt-3 text-sm text-red-600" onClick={() => remove.mutate(item.id)}>
              Delete
            </button>
          </article>
        ))}
      </div>
      {open && (
        <Modal title="New template" onClose={() => setOpen(false)}>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              create.mutate({
                name: f.get("name"),
                marketplace: f.get("marketplace"),
                productType: f.get("productType"),
                attributes: { fabric: f.get("fabric"), sleeveType: f.get("sleeveType"), fit: f.get("fit") },
              });
            }}
          >
            <input name="name" required placeholder="Men Shirt Template" className="w-full rounded-xl border px-3 py-2" />
            <select name="marketplace" className="w-full rounded-xl border px-3 py-2">
              <option>MEESHO</option>
              <option>AMAZON</option>
              <option>FLIPKART</option>
            </select>
            <input name="productType" placeholder="Shirt" className="w-full rounded-xl border px-3 py-2" />
            <input name="fabric" placeholder="Cotton" className="w-full rounded-xl border px-3 py-2" />
            <input name="sleeveType" placeholder="Full Sleeve" className="w-full rounded-xl border px-3 py-2" />
            <input name="fit" placeholder="Regular Fit" className="w-full rounded-xl border px-3 py-2" />
            <button className="w-full rounded-xl bg-teal-700 py-2 text-white">Save</button>
          </form>
        </Modal>
      )}
    </CrudScreen>
  );
}

export function ProfilesPage() {
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ["profiles"], queryFn: async () => (await api.get("/profiles")).data.data });
  const [open, setOpen] = useState(false);
  const create = useMutation({
    mutationFn: (body: object) => api.post("/profiles", body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["profiles"] });
      setOpen(false);
    },
  });
  const items = data?.content || [];
  return (
    <CrudScreen title="Autofill profiles" subtitle="Store GST, HSN, packer, and packaging defaults for the extension." actionLabel="Create profile" onAction={() => setOpen(true)}>
      {items.length === 0 && <Empty text="No profiles yet. Capture common supplier fields once and reuse them." />}
      <div className="grid gap-4 md:grid-cols-3">
        {items.map((item: { id: string; name: string; marketplace: string }) => (
          <article key={item.id} className="rounded-2xl border border-slate-200 bg-white p-5">
            <h3 className="font-medium">{item.name}</h3>
            <p className="text-sm text-slate-500">{item.marketplace}</p>
          </article>
        ))}
      </div>
      {open && (
        <Modal title="New autofill profile" onClose={() => setOpen(false)}>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              create.mutate({
                name: f.get("name"),
                marketplace: f.get("marketplace"),
                profileJson: {
                  gst: f.get("gst"),
                  hsn: f.get("hsn"),
                  countryOfOrigin: f.get("origin"),
                  netWeight: f.get("netWeight"),
                  manufacturerName: f.get("manufacturerName"),
                  manufacturerAddress: f.get("address"),
                  address: f.get("address"),
                  pincode: f.get("pincode"),
                  packerPincode: f.get("pincode"),
                },
              });
            }}
          >
            <input name="name" required placeholder="Men Shirt Standard" className="w-full rounded-xl border px-3 py-2" />
            <select name="marketplace" className="w-full rounded-xl border px-3 py-2">
              <option>MEESHO</option>
              <option>AMAZON</option>
              <option>FLIPKART</option>
            </select>
            <input name="gst" placeholder="GST" className="w-full rounded-xl border px-3 py-2" />
            <input name="hsn" placeholder="HSN Code" className="w-full rounded-xl border px-3 py-2" />
            <input name="netWeight" placeholder="Net weight (gms)" className="w-full rounded-xl border px-3 py-2" />
            <input name="origin" placeholder="Country of origin" className="w-full rounded-xl border px-3 py-2" />
            <input name="manufacturerName" placeholder="Manufacturer / store name" className="w-full rounded-xl border px-3 py-2" />
            <input name="address" placeholder="Manufacturer / packer address" className="w-full rounded-xl border px-3 py-2" />
            <input name="pincode" placeholder="Pincode" className="w-full rounded-xl border px-3 py-2" />
            <button className="w-full rounded-xl bg-teal-700 py-2 text-white">Save</button>
          </form>
        </Modal>
      )}
    </CrudScreen>
  );
}

export function CrudScreen({
  title,
  subtitle,
  actionLabel,
  onAction,
  children,
}: {
  title: string;
  subtitle: string;
  actionLabel: string;
  onAction: () => void;
  children: ReactNode;
}) {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">{title}</h1>
          <p className="text-slate-500">{subtitle}</p>
        </div>
        <button onClick={onAction} className="rounded-xl bg-teal-700 px-4 py-2 text-white">
          {actionLabel}
        </button>
      </div>
      {children}
    </div>
  );
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="w-full max-w-md rounded-2xl bg-white p-6">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-semibold">{title}</h2>
          <button onClick={onClose}>Close</button>
        </div>
        {children}
      </div>
    </div>
  );
}
