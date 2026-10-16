import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { BadgeCheck, ShieldAlert } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import { formatDateTime, type WholesaleCategory, type WholesaleStore } from "./wholesale";

type StoreForm = {
  storeName: string;
  gstin: string;
  description: string;
  city: string;
  state: string;
  phone: string;
  email: string;
  categoryIds: string[];
};

type GstLookup = {
  gstin: string;
  legalName?: string | null;
  tradeName?: string | null;
  storeName: string;
  status?: string | null;
};

const EMPTY_FORM: StoreForm = {
  storeName: "",
  gstin: "",
  description: "",
  city: "",
  state: "",
  phone: "",
  email: "",
  categoryIds: [],
};

const inputClass =
  "w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100 dark:border-slate-700 dark:bg-slate-900";

export function SupplierStorePage() {
  const qc = useQueryClient();
  const [form, setForm] = useState<StoreForm>(EMPTY_FORM);
  const [saved, setSaved] = useState("");
  const [gstLookup, setGstLookup] = useState<GstLookup | null>(null);

  const categories = useQuery({
    queryKey: ["ws-categories"],
    queryFn: async () => (await api.get("/wholesale/categories")).data.data as WholesaleCategory[],
    staleTime: 60_000,
  });

  const store = useQuery({
    queryKey: ["ws-my-store"],
    queryFn: async () => ((await api.get("/wholesale/supplier/store")).data.data as WholesaleStore | undefined) ?? null,
    retry: false,
  });

  const notSupplier = axios.isAxiosError(store.error) && store.error.response?.status === 403;
  const current = store.data;

  useEffect(() => {
    if (!current) return;
    setForm({
      storeName: current.storeName,
      gstin: current.gstin || "",
      description: current.description || "",
      city: current.city || "",
      state: current.state || "",
      phone: current.phone || "",
      email: current.email || "",
      categoryIds: current.categories.map((c) => c.id),
    });
    setGstLookup(null);
  }, [current]);

  const lookupGst = useMutation({
    mutationFn: async (gstin: string) =>
      (await api.post("/wholesale/supplier/gst/lookup", { gstin })).data.data as GstLookup,
    onSuccess: (data) => {
      setGstLookup(data);
      setForm((f) => ({
        ...f,
        gstin: data.gstin,
        storeName: data.storeName,
      }));
      setSaved("");
    },
    onError: () => {
      setGstLookup(null);
      setForm((f) => ({ ...f, storeName: "" }));
    },
  });

  const save = useMutation({
    mutationFn: async () => {
      if (current) {
        return (
          await api.put("/wholesale/supplier/store", {
            description: form.description,
            city: form.city,
            state: form.state,
            phone: form.phone,
            email: form.email,
            categoryIds: form.categoryIds,
          })
        ).data;
      }
      return (
        await api.post("/wholesale/supplier/store", {
          gstin: form.gstin.trim().toUpperCase(),
          storeName: form.storeName,
          description: form.description,
          city: form.city,
          state: form.state,
          phone: form.phone,
          email: form.email,
          categoryIds: form.categoryIds,
        })
      ).data;
    },
    onSuccess: async (result) => {
      setSaved(result?.message || "Saved");
      await qc.invalidateQueries({ queryKey: ["ws-my-store"] });
    },
    onError: () => setSaved(""),
  });

  function update<K extends keyof StoreForm>(key: K, value: StoreForm[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  function onGstinChange(value: string) {
    const gstin = value.toUpperCase();
    setGstLookup(null);
    setForm((f) => ({ ...f, gstin, storeName: "" }));
  }

  function verifyGst() {
    const gstin = form.gstin.trim().toUpperCase();
    if (gstin.length !== 15) return;
    lookupGst.mutate(gstin);
  }

  function toggleCategory(id: string) {
    setForm((f) => ({
      ...f,
      categoryIds: f.categoryIds.includes(id) ? f.categoryIds.filter((c) => c !== id) : [...f.categoryIds, id],
    }));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    setSaved("");
    if (!current && !gstLookup?.storeName) {
      verifyGst();
      return;
    }
    save.mutate();
  }

  if (store.isLoading) return <p className="text-sm text-slate-500">Loading store...</p>;

  if (notSupplier) {
    return (
      <div className="mx-auto max-w-2xl space-y-3 rounded-2xl border border-amber-200 bg-amber-50 p-6 text-sm text-amber-950">
        <p className="flex items-center gap-2 text-base font-semibold">
          <ShieldAlert size={18} /> Supplier plan required
        </p>
        <p>
          Opening a wholesale store needs the <strong>SUPPLIER</strong> plan (or a plan with wholesale supplier access).
        </p>
        <Link to="/subscription" className="inline-block rounded-xl bg-teal-700 px-4 py-2 font-semibold text-white">
          View plans
        </Link>
      </div>
    );
  }

  if (store.isError) return <p className="text-sm text-red-600">{apiErrorMessage(store.error)}</p>;

  const verified = current?.gstVerificationStatus === "VERIFIED";
  const storeNameLocked = Boolean(current) || Boolean(gstLookup?.storeName);
  const canCreate = Boolean(current) || Boolean(gstLookup?.storeName);

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">{current ? "My supplier store" : "Open your wholesale store"}</h1>
        <p className="text-sm text-slate-500">
          {current
            ? "Buyers see your store name, city and categories. Phone and email are shared only after you accept an order."
            : "Enter your GSTIN first. We fill the store name from the GST trade name (or legal name) and lock it."}
        </p>
      </div>

      {current && (
        <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-slate-200 bg-white p-4 text-sm dark:border-slate-800 dark:bg-slate-900">
          <span
            className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${
              verified ? "bg-teal-100 text-teal-800" : "bg-amber-100 text-amber-800"
            }`}
          >
            <BadgeCheck size={14} /> GST {current.gstVerificationStatus}
          </span>
          <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">{current.status}</span>
          {current.gstLegalName && <span className="text-slate-600">Registered name: {current.gstLegalName}</span>}
          {current.gstVerifiedAt && <span className="text-xs text-slate-400">Verified {formatDateTime(current.gstVerifiedAt)}</span>}
          <span className="ml-auto text-slate-500">{current.productCount} live products</span>
        </div>
      )}

      {!current && (
        <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-300">
          1) Enter GSTIN → 2) Verify → store name auto-fills from <strong>trade name</strong> (else legal name) and becomes
          read-only → 3) Complete details and create store.
        </div>
      )}

      <form onSubmit={submit} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="space-y-1 text-sm sm:col-span-2">
            <span className="font-medium">GSTIN</span>
            <div className="flex flex-col gap-2 sm:flex-row">
              <input
                required
                minLength={15}
                maxLength={15}
                value={form.gstin}
                disabled={Boolean(current)}
                onChange={(e) => onGstinChange(e.target.value)}
                onBlur={() => {
                  if (!current && form.gstin.trim().length === 15 && !gstLookup) verifyGst();
                }}
                placeholder="27AAPFU0939F1ZV"
                className={`${inputClass} font-mono uppercase disabled:bg-slate-50 disabled:text-slate-500`}
              />
              {!current && (
                <button
                  type="button"
                  onClick={verifyGst}
                  disabled={lookupGst.isPending || form.gstin.trim().length !== 15}
                  className="shrink-0 rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-50"
                >
                  {lookupGst.isPending ? "Verifying..." : "Verify GST"}
                </button>
              )}
            </div>
            {lookupGst.isError && (
              <p className="text-xs text-red-600" role="alert">
                {apiErrorMessage(lookupGst.error)}
              </p>
            )}
            {gstLookup && (
              <p className="text-xs text-teal-800">
                GST {gstLookup.status || "Active"}
                {gstLookup.tradeName
                  ? ` · Trade name: ${gstLookup.tradeName}`
                  : gstLookup.legalName
                    ? ` · Legal name: ${gstLookup.legalName}`
                    : ""}
              </p>
            )}
          </label>

          <label className="space-y-1 text-sm sm:col-span-2">
            <span className="font-medium">Store name</span>
            <input
              required
              maxLength={160}
              value={form.storeName}
              disabled
              readOnly
              placeholder={current ? undefined : "Verify GSTIN to auto-fill from trade name"}
              className={`${inputClass} disabled:bg-slate-50 disabled:text-slate-600`}
            />
            {!current && (
              <p className="text-xs text-slate-500">
                {storeNameLocked
                  ? "Locked to GST trade name (or legal name if trade name is empty). Not editable."
                  : "Will be filled automatically after GST verification."}
              </p>
            )}
          </label>

          <label className="space-y-1 text-sm">
            <span className="font-medium">City</span>
            <input required value={form.city} onChange={(e) => update("city", e.target.value)} className={inputClass} />
          </label>
          <label className="space-y-1 text-sm">
            <span className="font-medium">State</span>
            <input required value={form.state} onChange={(e) => update("state", e.target.value)} className={inputClass} />
          </label>
          <label className="space-y-1 text-sm">
            <span className="font-medium">Phone</span>
            <input
              required
              type="tel"
              value={form.phone}
              onChange={(e) => update("phone", e.target.value)}
              placeholder="9876543210"
              className={inputClass}
            />
          </label>
          <label className="space-y-1 text-sm">
            <span className="font-medium">Email</span>
            <input required type="email" value={form.email} onChange={(e) => update("email", e.target.value)} className={inputClass} />
          </label>
        </div>

        <label className="block space-y-1 text-sm">
          <span className="font-medium">About your store</span>
          <textarea
            rows={3}
            maxLength={4000}
            value={form.description}
            onChange={(e) => update("description", e.target.value)}
            className={inputClass}
          />
        </label>

        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">Categories you supply</legend>
          <div className="flex flex-wrap gap-2">
            {(categories.data || []).map((category) => {
              const active = form.categoryIds.includes(category.id);
              return (
                <button
                  key={category.id}
                  type="button"
                  onClick={() => toggleCategory(category.id)}
                  aria-pressed={active}
                  className={`rounded-full border px-3 py-1.5 text-sm ${
                    active
                      ? "border-teal-700 bg-teal-700 text-white"
                      : "border-slate-200 bg-white text-slate-700 hover:border-teal-300 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                  }`}
                >
                  {category.name}
                </button>
              );
            })}
          </div>
        </fieldset>

        {save.isError && (
          <p className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
            {apiErrorMessage(save.error)}
          </p>
        )}
        {saved && <p className="text-sm text-teal-800">{saved}</p>}

        <div className="flex flex-wrap items-center gap-3">
          <button
            type="submit"
            disabled={save.isPending || lookupGst.isPending || form.categoryIds.length === 0 || (!current && !canCreate)}
            className="rounded-xl bg-teal-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50"
          >
            {save.isPending
              ? current
                ? "Saving..."
                : "Creating store..."
              : current
                ? "Save changes"
                : "Create store"}
          </button>
          {!current && !canCreate && (
            <span className="text-xs text-slate-500">Verify GSTIN first to unlock store creation.</span>
          )}
          {form.categoryIds.length === 0 && <span className="text-xs text-slate-500">Select at least one category.</span>}
          {current && (
            <Link to="/supplier/inventory" className="text-sm font-medium text-teal-700 hover:underline">
              Manage inventory
            </Link>
          )}
        </div>
      </form>
    </div>
  );
}
