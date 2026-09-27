import { type FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, apiErrorMessage } from "../../api/client";
import type { BillingPromoOption, BillingSettings } from "../../types";
import { useAuth } from "../../store/auth";
import { isSuperAdmin } from "../../routes/roles";

const ACTIVATE_SQL = `-- Change email + plan_name (BASIC | STARTER | PRO | BUSINESS), then run.
WITH params AS (
    SELECT 'user@example.com'::text AS email, 'BASIC'::text AS plan_name
)
UPDATE subscriptions s
SET
    plan_id          = p.id,
    pending_plan_id  = NULL,
    status           = 'ACTIVE',
    start_date       = CURRENT_DATE,
    end_date         = (CURRENT_DATE + INTERVAL '1 month')::date,
    trial_started_at = COALESCE(s.trial_started_at, NOW()),
    updated_at       = NOW()
FROM params
JOIN users u ON lower(u.email) = lower(params.email)
JOIN subscription_plans p ON p.name = params.plan_name
WHERE s.user_id = u.id
  AND s.id = (
      SELECT s2.id FROM subscriptions s2
      WHERE s2.user_id = u.id
      ORDER BY s2.created_at DESC
      LIMIT 1
  );`;

export function BillingAdminPage() {
  const { user } = useAuth();
  const superAdmin = isSuperAdmin(user?.role);
  const qc = useQueryClient();
  const { data, isLoading, error } = useQuery({
    queryKey: ["admin-billing"],
    queryFn: async () => (await api.get("/admin/billing")).data.data as BillingSettings,
  });
  const promos = useQuery({
    queryKey: ["admin-billing-promos"],
    queryFn: async () => (await api.get("/admin/billing/promos")).data.data as BillingPromoOption[],
  });
  const save = useMutation({
    mutationFn: (payload: BillingSettings) => api.put("/admin/billing", payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-billing"] }),
  });
  const createPromo = useMutation({
    mutationFn: (payload: Record<string, unknown>) => api.post("/admin/billing/promos", payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-billing-promos"] }),
  });
  const disablePromo = useMutation({
    mutationFn: (id: string) => api.delete(`/admin/billing/promos/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-billing-promos"] }),
  });
  const [promoMsg, setPromoMsg] = useState("");

  if (isLoading) return <p>Loading billing settings...</p>;
  if (error || !data) return <p className="text-red-600">{apiErrorMessage(error, "Could not load billing settings")}</p>;

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const f = new FormData(event.currentTarget);
    save.mutate({
      trialDays: Number(f.get("trialDays") || 2),
      trialPlan: String(f.get("trialPlan") || "BASIC"),
      whatsappNumber: String(f.get("whatsappNumber") || ""),
      whatsappMessageTemplate: String(f.get("whatsappMessageTemplate") || ""),
      upiId: String(f.get("upiId") || ""),
      payeeName: String(f.get("payeeName") || ""),
      qrImageUrl: String(f.get("qrImageUrl") || ""),
      paymentProvider: String(f.get("paymentProvider") || "MANUAL"),
      paymentInstructions: String(f.get("paymentInstructions") || ""),
      rechargeHeadline: String(f.get("rechargeHeadline") || ""),
      rechargeBody: String(f.get("rechargeBody") || ""),
      companyLegalName: String(f.get("companyLegalName") || "Catalog Studio"),
      companyGstin: String(f.get("companyGstin") || "19CMZPM0096H1ZA"),
      parentCompanyName: String(f.get("parentCompanyName") || "Shritaji"),
      gstPercent: Number(f.get("gstPercent") || 18),
      serviceChargePercent: Number(f.get("serviceChargePercent") || 0),
    });
  }

  function onCreatePromo(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPromoMsg("");
    const f = new FormData(event.currentTarget);
    createPromo.mutate(
      {
        code: String(f.get("code") || ""),
        description: String(f.get("description") || ""),
        discountType: String(f.get("discountType") || "PERCENT"),
        discountValue: Number(f.get("discountValue") || 0),
        maxUses: f.get("maxUses") ? Number(f.get("maxUses")) : null,
        status: "ACTIVE",
      },
      {
        onSuccess: () => {
          setPromoMsg("Promo code added. It appears in the payment dropdown.");
          event.currentTarget.reset();
        },
      },
    );
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold">Trial and payment</h1>
        <p className="text-slate-500">
          Trial days, PhonePe scanner, GST, and promo codes. Service charge can be changed only by a super admin.
        </p>
      </div>
      <form onSubmit={onSubmit} className="space-y-3 rounded-2xl border border-slate-200 bg-white p-6">
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm font-medium">
            Free trial days
            <input name="trialDays" type="number" min={0} defaultValue={data.trialDays} className="mt-1 w-full rounded-xl border px-3 py-2" />
          </label>
          <label className="block text-sm font-medium">
            Trial plan
            <input name="trialPlan" defaultValue={data.trialPlan} className="mt-1 w-full rounded-xl border px-3 py-2" />
          </label>
          <label className="block text-sm font-medium">
            WhatsApp number (with country code)
            <input name="whatsappNumber" defaultValue={data.whatsappNumber} className="mt-1 w-full rounded-xl border px-3 py-2" />
          </label>
          <label className="block text-sm font-medium">
            UPI ID
            <input name="upiId" defaultValue={data.upiId} className="mt-1 w-full rounded-xl border px-3 py-2" />
          </label>
          <label className="block text-sm font-medium">
            Payee name
            <input name="payeeName" defaultValue={data.payeeName} className="mt-1 w-full rounded-xl border px-3 py-2" />
          </label>
          <label className="block text-sm font-medium">
            Payment provider
            <select name="paymentProvider" defaultValue={data.paymentProvider || "MANUAL"} className="mt-1 w-full rounded-xl border px-3 py-2">
              <option value="MANUAL">Scanner + WhatsApp (live)</option>
              <option value="RAZORPAY">Razorpay (not enabled yet)</option>
            </select>
          </label>
          <label className="block text-sm font-medium sm:col-span-2">
            Scanner screenshot URL
            <input name="qrImageUrl" defaultValue={data.qrImageUrl || "/payment-qr.jpg"} className="mt-1 w-full rounded-xl border px-3 py-2" placeholder="/payment-qr.jpg" />
          </label>
        </div>

        <div className="rounded-2xl border border-teal-100 bg-teal-50/50 p-4">
          <h2 className="text-sm font-semibold text-teal-900">Company tax & parent</h2>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <label className="block text-sm font-medium">
              Legal name
              <input name="companyLegalName" defaultValue={data.companyLegalName || "Catalog Studio"} className="mt-1 w-full rounded-xl border px-3 py-2" />
            </label>
            <label className="block text-sm font-medium">
              Parent company
              <input name="parentCompanyName" defaultValue={data.parentCompanyName || "Shritaji"} className="mt-1 w-full rounded-xl border px-3 py-2" />
            </label>
            <label className="block text-sm font-medium">
              Company GSTIN
              <input
                name="companyGstin"
                defaultValue={data.companyGstin || "19CMZPM0096H1ZA"}
                className="mt-1 w-full rounded-xl border px-3 py-2 uppercase"
                placeholder="19CMZPM0096H1ZA"
              />
            </label>
            <label className="block text-sm font-medium">
              GST %
              <input name="gstPercent" type="number" min={0} step="0.01" defaultValue={data.gstPercent ?? 18} className="mt-1 w-full rounded-xl border px-3 py-2" />
            </label>
            <label className="block text-sm font-medium sm:col-span-2">
              Service charge %
              <input
                name="serviceChargePercent"
                type="number"
                min={0}
                step="0.01"
                defaultValue={data.serviceChargePercent ?? 0}
                disabled={!superAdmin}
                className="mt-1 w-full rounded-xl border px-3 py-2 disabled:bg-slate-100"
              />
              {!superAdmin && (
                <span className="mt-1 block text-xs text-slate-500">Only a super admin can change the service charge.</span>
              )}
            </label>
          </div>
        </div>

        <div className="rounded-2xl bg-black p-3 sm:max-w-xs">
          <p className="text-xs font-semibold uppercase tracking-wide text-violet-300">Current scanner</p>
          <img src={data.qrImageUrl || "/payment-qr.jpg"} alt="Configured payment scanner" className="mt-2 w-full rounded-xl object-contain" />
        </div>
        <label className="block text-sm font-medium">
          WhatsApp message template
          <textarea name="whatsappMessageTemplate" defaultValue={data.whatsappMessageTemplate} rows={3} className="mt-1 w-full rounded-xl border px-3 py-2" />
          <span className="mt-1 block text-xs text-slate-500">Placeholders: {"{{plan}} {{amount}} {{email}} {{upi}} {{payee}} {{promo}}"}</span>
        </label>
        <label className="block text-sm font-medium">
          Payment instructions
          <textarea name="paymentInstructions" defaultValue={data.paymentInstructions} rows={3} className="mt-1 w-full rounded-xl border px-3 py-2" />
        </label>
        <label className="block text-sm font-medium">
          Recharge headline
          <input name="rechargeHeadline" defaultValue={data.rechargeHeadline} className="mt-1 w-full rounded-xl border px-3 py-2" />
        </label>
        <label className="block text-sm font-medium">
          Recharge body
          <textarea name="rechargeBody" defaultValue={data.rechargeBody} rows={3} className="mt-1 w-full rounded-xl border px-3 py-2" />
        </label>
        <button disabled={save.isPending} className="rounded-xl bg-teal-700 px-4 py-2 text-white">
          {save.isPending ? "Saving..." : "Save billing settings"}
        </button>
        {save.isError && <p className="text-sm text-red-600">{apiErrorMessage(save.error)}</p>}
        {save.isSuccess && <p className="text-sm text-teal-800">Saved.</p>}
      </form>

      <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6">
        <div>
          <h2 className="font-medium">Subscription promo codes</h2>
          <p className="mt-1 text-sm text-slate-500">
            Admin and super admin can add codes. Active codes appear in the seller payment dropdown.
          </p>
        </div>
        <form onSubmit={onCreatePromo} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <label className="block text-sm font-medium">
            Code
            <input name="code" required className="mt-1 w-full rounded-xl border px-3 py-2 uppercase" placeholder="WELCOME10" />
          </label>
          <label className="block text-sm font-medium">
            Type
            <select name="discountType" className="mt-1 w-full rounded-xl border px-3 py-2">
              <option value="PERCENT">Percent</option>
              <option value="FIXED">Fixed ₹</option>
            </select>
          </label>
          <label className="block text-sm font-medium">
            Value
            <input name="discountValue" type="number" min={0} step="0.01" required defaultValue={10} className="mt-1 w-full rounded-xl border px-3 py-2" />
          </label>
          <label className="block text-sm font-medium">
            Max uses
            <input name="maxUses" type="number" min={1} className="mt-1 w-full rounded-xl border px-3 py-2" placeholder="Unlimited" />
          </label>
          <label className="block text-sm font-medium sm:col-span-2 lg:col-span-1">
            Description
            <input name="description" className="mt-1 w-full rounded-xl border px-3 py-2" placeholder="Launch offer" />
          </label>
          <div className="flex items-end sm:col-span-2 lg:col-span-5">
            <button type="submit" disabled={createPromo.isPending} className="rounded-xl bg-teal-700 px-4 py-2 text-sm text-white">
              {createPromo.isPending ? "Adding…" : "Add promo"}
            </button>
          </div>
        </form>
        {promoMsg && <p className="text-sm text-teal-800">{promoMsg}</p>}
        {createPromo.isError && <p className="text-sm text-red-600">{apiErrorMessage(createPromo.error)}</p>}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-slate-500">
              <tr>
                <th className="py-2">Code</th>
                <th>Discount</th>
                <th>Uses</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {(promos.data || []).map((promo) => (
                <tr key={promo.id} className="border-t">
                  <td className="py-2 font-medium">{promo.code}</td>
                  <td>
                    {promo.discountType === "PERCENT"
                      ? `${Number(promo.discountValue)}%`
                      : `₹${Number(promo.discountValue)}`}
                    {promo.description ? ` · ${promo.description}` : ""}
                  </td>
                  <td>{promo.usedCount ?? 0}{promo.maxUses != null ? ` / ${promo.maxUses}` : ""}</td>
                  <td>{promo.status}</td>
                  <td className="text-right">
                    {promo.status === "ACTIVE" && (
                      <button
                        type="button"
                        className="text-xs text-rose-700"
                        onClick={() => disablePromo.mutate(promo.id)}
                      >
                        Disable
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {!promos.isLoading && (promos.data || []).length === 0 && (
                <tr>
                  <td className="py-4 text-slate-500" colSpan={5}>
                    No promo codes yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-6">
        <h2 className="font-medium">Activate a seller by email</h2>
        <p className="mt-1 text-sm text-slate-500">
          Live operators should use the Users menu instead: select the seller, choose the plan, and click Activate so
          Catalog Studio writes the Transactions row automatically and shows whether a promo was applied.
        </p>
        <pre className="mt-4 overflow-x-auto rounded-xl bg-slate-950 p-4 text-xs text-slate-100">{ACTIVATE_SQL}</pre>
      </section>
    </div>
  );
}
