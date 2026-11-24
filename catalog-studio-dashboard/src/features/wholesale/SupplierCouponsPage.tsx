import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { TicketPercent, Trash2 } from "lucide-react";
import { useState, type FormEvent } from "react";
import { api, apiErrorMessage } from "../../api/client";
import { useI18n } from "../../i18n/LanguageProvider";
import { formatInr, type WholesaleCoupon } from "./wholesale";

const inputClass =
  "w-full rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-sm outline-none focus:border-teal-600 dark:border-slate-700 dark:bg-slate-900";

export function SupplierCouponsPage() {
  const { t } = useI18n();
  const qc = useQueryClient();
  const [code, setCode] = useState("");
  const [discountType, setDiscountType] = useState<"PERCENT" | "FIXED">("PERCENT");
  const [discountValue, setDiscountValue] = useState("");
  const [minOrder, setMinOrder] = useState("");
  const [maxDiscount, setMaxDiscount] = useState("");
  const [usageLimit, setUsageLimit] = useState("");

  const coupons = useQuery({
    queryKey: ["ws-supplier-coupons"],
    queryFn: async () => (await api.get("/wholesale/supplier/coupons")).data.data as WholesaleCoupon[],
  });

  const create = useMutation({
    mutationFn: async () =>
      api.post("/wholesale/supplier/coupons", {
        code: code.trim().toUpperCase(),
        discountType,
        discountValue: Number(discountValue),
        minOrder: minOrder ? Number(minOrder) : 0,
        maxDiscount: maxDiscount ? Number(maxDiscount) : undefined,
        usageLimit: usageLimit ? Number(usageLimit) : undefined,
        active: true,
      }),
    onSuccess: async () => {
      setCode("");
      setDiscountValue("");
      setMinOrder("");
      setMaxDiscount("");
      setUsageLimit("");
      await qc.invalidateQueries({ queryKey: ["ws-supplier-coupons"] });
    },
  });

  const remove = useMutation({
    mutationFn: (id: string) => api.delete(`/wholesale/supplier/coupons/${id}`),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["ws-supplier-coupons"] });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    create.mutate();
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Store coupons</h1>
        <p className="mt-1 text-sm text-slate-500">
          Create discount codes for your wholesale store only. Share the code with buyers; they apply it at checkout and see GST on the bill.
        </p>
      </div>

      <form onSubmit={submit} className="space-y-3 rounded-2xl border border-teal-200 bg-white p-5 dark:border-teal-900 dark:bg-slate-900">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="space-y-1 text-sm">
            <span className="font-medium">Code</span>
            <input required maxLength={40} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="FESTIVE10" className={inputClass} />
          </label>
          <label className="space-y-1 text-sm">
            <span className="font-medium">Type</span>
            <select value={discountType} onChange={(e) => setDiscountType(e.target.value as "PERCENT" | "FIXED")} className={inputClass}>
              <option value="PERCENT">Percent (%)</option>
              <option value="FIXED">Fixed (₹)</option>
            </select>
          </label>
          <label className="space-y-1 text-sm">
            <span className="font-medium">Value</span>
            <input required type="number" min={0.01} step="0.01" value={discountValue} onChange={(e) => setDiscountValue(e.target.value)} className={inputClass} />
          </label>
          <label className="space-y-1 text-sm">
            <span className="font-medium">Min order (₹)</span>
            <input type="number" min={0} step="0.01" value={minOrder} onChange={(e) => setMinOrder(e.target.value)} className={inputClass} />
          </label>
          <label className="space-y-1 text-sm">
            <span className="font-medium">Max discount (₹, optional)</span>
            <input type="number" min={0} step="0.01" value={maxDiscount} onChange={(e) => setMaxDiscount(e.target.value)} className={inputClass} />
          </label>
          <label className="space-y-1 text-sm">
            <span className="font-medium">Usage limit (optional)</span>
            <input type="number" min={1} value={usageLimit} onChange={(e) => setUsageLimit(e.target.value)} className={inputClass} />
          </label>
        </div>
        {create.isError && <p className="text-sm text-red-600">{apiErrorMessage(create.error)}</p>}
        <button type="submit" disabled={create.isPending} className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-50">
          {create.isPending ? t("common.saving") : "Create coupon"}
        </button>
      </form>

      {coupons.isLoading && <p className="text-sm text-slate-500">{t("common.loading")}</p>}
      {coupons.isError && <p className="text-sm text-red-600">{apiErrorMessage(coupons.error)}</p>}
      <div className="space-y-2">
        {(coupons.data || []).map((coupon) => (
          <div key={coupon.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 dark:border-slate-800 dark:bg-slate-900">
            <div className="flex items-start gap-3">
              <TicketPercent className="mt-0.5 text-teal-700" size={18} />
              <div>
                <p className="font-semibold tracking-wide">{coupon.code}</p>
                <p className="text-sm text-slate-500">
                  {coupon.discountType === "PERCENT" ? `${coupon.discountValue}%` : formatInr(coupon.discountValue)}
                  {coupon.minOrder ? ` · min ${formatInr(coupon.minOrder)}` : ""}
                  {` · used ${coupon.usedCount}${coupon.usageLimit != null ? `/${coupon.usageLimit}` : ""}`}
                  {coupon.active ? "" : " · inactive"}
                </p>
              </div>
            </div>
            <button
              type="button"
              aria-label="Delete coupon"
              onClick={() => remove.mutate(coupon.id)}
              className="rounded-lg p-2 text-slate-500 hover:bg-red-50 hover:text-red-600"
            >
              <Trash2 size={16} />
            </button>
          </div>
        ))}
        {coupons.data && coupons.data.length === 0 && <p className="text-sm text-slate-500">No coupons yet.</p>}
      </div>
    </div>
  );
}
