import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { BadgeCheck, ShieldAlert } from "lucide-react";
import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import { useI18n } from "../../i18n/LanguageProvider";
import { formatDateTime, type WholesaleCategory, type WholesaleStore } from "./wholesale";

type StoreForm = {
  storeName: string;
  gstin: string;
  description: string;
  city: string;
  state: string;
  addressLine: string;
  pincode: string;
  pan: string;
  phone: string;
  email: string;
  gstPercent: string;
  bankAccountName: string;
  bankAccountNumber: string;
  bankName: string;
  bankIfsc: string;
  bankBranch: string;
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
  addressLine: "",
  pincode: "",
  pan: "",
  phone: "",
  email: "",
  gstPercent: "18",
  bankAccountName: "",
  bankAccountNumber: "",
  bankName: "",
  bankIfsc: "",
  bankBranch: "",
  categoryIds: [],
};

const inputClass =
  "w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100 dark:border-slate-700 dark:bg-slate-900";

export function SupplierStorePage() {
  const { t } = useI18n();
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
      addressLine: current.addressLine || "",
      pincode: current.pincode || "",
      pan: current.pan || "",
      phone: current.phone || "",
      email: current.email || "",
      gstPercent: String(current.gstPercent ?? 18),
      bankAccountName: current.bankAccountName || "",
      bankAccountNumber: current.bankAccountNumber || "",
      bankName: current.bankName || "",
      bankIfsc: current.bankIfsc || "",
      bankBranch: current.bankBranch || "",
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
            addressLine: form.addressLine,
            pincode: form.pincode,
            pan: form.pan,
            phone: form.phone,
            email: form.email,
            gstPercent: Number(form.gstPercent) || 18,
            bankAccountName: form.bankAccountName,
            bankAccountNumber: form.bankAccountNumber,
            bankName: form.bankName,
            bankIfsc: form.bankIfsc,
            bankBranch: form.bankBranch,
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
          gstPercent: Number(form.gstPercent) || 18,
          categoryIds: form.categoryIds,
        })
      ).data;
    },
    onSuccess: async (result) => {
      setSaved(result?.message || t("store.saved"));
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

  if (store.isLoading) return <p className="text-sm text-slate-500">{t("store.loading")}</p>;

  if (notSupplier) {
    return (
      <div className="mx-auto max-w-2xl space-y-3 rounded-2xl border border-amber-200 bg-amber-50 p-6 text-sm text-amber-950">
        <p className="flex items-center gap-2 text-base font-semibold">
          <ShieldAlert size={18} /> {t("store.planRequiredTitle")}
        </p>
        <p>{t("store.planRequiredBody")}</p>
        <Link to="/subscription" className="inline-block rounded-xl bg-teal-700 px-4 py-2 font-semibold text-white">
          {t("common.viewPlans")}
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
        <h1 className="text-2xl font-semibold">{current ? t("store.titleMine") : t("store.titleCreate")}</h1>
        <p className="text-sm text-slate-500">{current ? t("store.subMine") : t("store.subCreate")}</p>
      </div>

      {current && (
        <div className="flex flex-wrap items-center gap-3 rounded-2xl border border-slate-200 bg-white p-4 text-sm dark:border-slate-800 dark:bg-slate-900">
          <span
            className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${
              verified ? "bg-teal-100 text-teal-800" : "bg-amber-100 text-amber-800"
            }`}
          >
            <BadgeCheck size={14} /> {t("store.gstLabel", { status: current.gstVerificationStatus || "PENDING" })}
          </span>
          <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">{current.status}</span>
          {current.gstLegalName && (
            <span className="text-slate-600">{t("store.registeredName", { name: current.gstLegalName })}</span>
          )}
          {current.gstVerifiedAt && (
            <span className="text-xs text-slate-400">{t("store.verifiedAt", { when: formatDateTime(current.gstVerifiedAt) })}</span>
          )}
          <span className="ml-auto text-slate-500">{t("store.liveProducts", { count: current.productCount })}</span>
        </div>
      )}

      {!current && (
        <div className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-300">
          {t("store.steps")}
        </div>
      )}

      <form onSubmit={submit} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="space-y-1 text-sm sm:col-span-2">
            <span className="font-medium">{t("store.gstin")}</span>
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
                  {lookupGst.isPending ? t("store.verifying") : t("store.verifyGst")}
                </button>
              )}
            </div>
            {!current && form.gstin.trim().length > 0 && form.gstin.trim().length !== 15 && (
              <div className="space-y-0.5 text-xs text-slate-500">
                <p>{t("store.gstinHintShort")}</p>
                <p>{t("store.gstinHintCount", { count: form.gstin.trim().length })}</p>
              </div>
            )}
            {lookupGst.isError && (
              <p className="text-xs text-red-600" role="alert">
                {apiErrorMessage(lookupGst.error)}
              </p>
            )}
            {gstLookup && (
              <p className="text-xs text-teal-800">
                {t("store.gstLabel", { status: gstLookup.status || "Active" })}
                {gstLookup.tradeName
                  ? t("store.tradeName", { name: gstLookup.tradeName })
                  : gstLookup.legalName
                    ? t("store.legalName", { name: gstLookup.legalName })
                    : ""}
              </p>
            )}
          </label>

          <label className="space-y-1 text-sm sm:col-span-2">
            <span className="font-medium">{t("store.storeName")}</span>
            <input
              required
              maxLength={160}
              value={form.storeName}
              disabled
              readOnly
              placeholder={current ? undefined : t("store.storeNamePlaceholder")}
              className={`${inputClass} disabled:bg-slate-50 disabled:text-slate-600`}
            />
            {!current && (
              <p className="text-xs text-slate-500">
                {storeNameLocked ? t("store.storeNameLocked") : t("store.storeNamePending")}
              </p>
            )}
          </label>

          <label className="space-y-1 text-sm">
            <span className="font-medium">{t("common.city")}</span>
            <input required value={form.city} onChange={(e) => update("city", e.target.value)} className={inputClass} />
          </label>
          <label className="space-y-1 text-sm">
            <span className="font-medium">{t("common.state")}</span>
            <input required value={form.state} onChange={(e) => update("state", e.target.value)} className={inputClass} />
          </label>
          {current && (
            <>
              <label className="space-y-1 text-sm sm:col-span-2">
                <span className="font-medium">Invoice address</span>
                <input
                  value={form.addressLine}
                  onChange={(e) => update("addressLine", e.target.value)}
                  placeholder="Street / locality for tax invoice"
                  className={inputClass}
                />
              </label>
              <label className="space-y-1 text-sm">
                <span className="font-medium">PIN code</span>
                <input value={form.pincode} onChange={(e) => update("pincode", e.target.value)} className={inputClass} />
              </label>
              <label className="space-y-1 text-sm">
                <span className="font-medium">PAN</span>
                <input
                  value={form.pan}
                  onChange={(e) => update("pan", e.target.value.toUpperCase())}
                  className={`${inputClass} font-mono uppercase`}
                />
              </label>
              <p className="sm:col-span-2 text-xs font-semibold uppercase tracking-wide text-slate-500">Bank details on invoice</p>
              <label className="space-y-1 text-sm">
                <span className="font-medium">Account name</span>
                <input value={form.bankAccountName} onChange={(e) => update("bankAccountName", e.target.value)} className={inputClass} />
              </label>
              <label className="space-y-1 text-sm">
                <span className="font-medium">Account number</span>
                <input value={form.bankAccountNumber} onChange={(e) => update("bankAccountNumber", e.target.value)} className={inputClass} />
              </label>
              <label className="space-y-1 text-sm">
                <span className="font-medium">Bank</span>
                <input value={form.bankName} onChange={(e) => update("bankName", e.target.value)} className={inputClass} />
              </label>
              <label className="space-y-1 text-sm">
                <span className="font-medium">IFSC</span>
                <input
                  value={form.bankIfsc}
                  onChange={(e) => update("bankIfsc", e.target.value.toUpperCase())}
                  className={`${inputClass} font-mono uppercase`}
                />
              </label>
              <label className="space-y-1 text-sm sm:col-span-2">
                <span className="font-medium">Branch</span>
                <input value={form.bankBranch} onChange={(e) => update("bankBranch", e.target.value)} className={inputClass} />
              </label>
            </>
          )}
          <label className="space-y-1 text-sm">
            <span className="font-medium">{t("common.phone")}</span>
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
            <span className="font-medium">{t("common.email")}</span>
            <input required type="email" value={form.email} onChange={(e) => update("email", e.target.value)} className={inputClass} />
          </label>
          <label className="space-y-1 text-sm">
            <span className="font-medium">GST % on bills</span>
            <input
              required
              type="number"
              min={0}
              max={100}
              step="0.01"
              value={form.gstPercent}
              onChange={(e) => update("gstPercent", e.target.value)}
              className={inputClass}
            />
          </label>
        </div>

        <label className="block space-y-1 text-sm">
          <span className="font-medium">{t("store.about")}</span>
          <textarea
            rows={3}
            maxLength={4000}
            value={form.description}
            onChange={(e) => update("description", e.target.value)}
            className={inputClass}
          />
        </label>

        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">{t("store.categories")}</legend>
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
                ? t("common.saving")
                : t("store.creating")
              : current
                ? t("store.saveChanges")
                : t("store.create")}
          </button>
          {!current && !canCreate && <span className="text-xs text-slate-500">{t("store.verifyFirst")}</span>}
          {form.categoryIds.length === 0 && <span className="text-xs text-slate-500">{t("store.selectCategory")}</span>}
          {current && (
            <Link to="/supplier/inventory" className="text-sm font-medium text-teal-700 hover:underline">
              {t("store.manageInventory")}
            </Link>
          )}
        </div>
      </form>
    </div>
  );
}
