import { type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, apiErrorMessage } from "../../api/client";
import type { BillingSettings } from "../../types";

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
  const qc = useQueryClient();
  const { data, isLoading, error } = useQuery({
    queryKey: ["admin-billing"],
    queryFn: async () => (await api.get("/admin/billing")).data.data as BillingSettings,
  });
  const save = useMutation({
    mutationFn: (payload: BillingSettings) => api.put("/admin/billing", payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["admin-billing"] }),
  });

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
    });
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold">Trial and payment</h1>
        <p className="text-slate-500">
          Trial days, the PhonePe scanner image, and WhatsApp number are stored in the database. Razorpay and other
          gateways can be switched on later from the payment provider field without changing this recharge screen.
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
        <div className="rounded-2xl bg-black p-3 sm:max-w-xs">
          <p className="text-xs font-semibold uppercase tracking-wide text-violet-300">Current scanner</p>
          <img src={data.qrImageUrl || "/payment-qr.jpg"} alt="Configured payment scanner" className="mt-2 w-full rounded-xl object-contain" />
        </div>
        <label className="block text-sm font-medium">
          WhatsApp message template
          <textarea name="whatsappMessageTemplate" defaultValue={data.whatsappMessageTemplate} rows={3} className="mt-1 w-full rounded-xl border px-3 py-2" />
          <span className="mt-1 block text-xs text-slate-500">Placeholders: {"{{plan}} {{amount}} {{email}} {{upi}} {{payee}}"}</span>
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
      <section className="rounded-2xl border border-slate-200 bg-white p-6">
        <h2 className="font-medium">Activate a seller by email</h2>
        <p className="mt-1 text-sm text-slate-500">
          Replace the email and plan name (`BASIC`, `STARTER`, `PRO`, or `BUSINESS`) after you confirm the WhatsApp screenshot. Also in
          {" "}
          <code>catalog-studio-backend/src/main/resources/db/scripts/activate_paid_access_by_email.sql</code>.
        </p>
        <pre className="mt-4 overflow-x-auto rounded-xl bg-slate-950 p-4 text-xs text-slate-100">{ACTIVATE_SQL}</pre>
      </section>
    </div>
  );
}
