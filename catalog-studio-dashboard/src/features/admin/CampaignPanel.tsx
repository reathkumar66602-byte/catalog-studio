import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, apiErrorMessage } from "../../api/client";

type CampaignCapabilities = {
  whatsappConfigured: boolean;
  whatsappMessage: string;
  emailEnabled: boolean;
};

type CampaignRun = {
  id: string;
  campaignType: string;
  channel: string;
  promoCode?: string | null;
  status: string;
  totalRecipients: number;
  sentCount: number;
  skippedCount: number;
  failedCount: number;
  createdAt: string;
  whatsappCapability?: string;
};

type PromoOption = {
  code: string;
  status: string;
  description?: string;
};

export function CampaignPanel() {
  const qc = useQueryClient();
  const [campaignType, setCampaignType] = useState("TRIAL_EXPIRED");
  const [channel, setChannel] = useState("EMAIL");
  const [promoCode, setPromoCode] = useState("");
  const [message, setMessage] = useState("");

  const caps = useQuery({
    queryKey: ["admin-campaign-caps"],
    queryFn: async () => (await api.get("/admin/campaigns/capabilities")).data.data as CampaignCapabilities,
  });
  const runs = useQuery({
    queryKey: ["admin-campaign-runs"],
    queryFn: async () => (await api.get("/admin/campaigns/runs")).data.data as CampaignRun[],
  });
  const promos = useQuery({
    queryKey: ["admin-billing-promos-lite"],
    queryFn: async () => {
      try {
        const res = await api.get("/admin/billing/promos");
        return (res.data.data || []) as PromoOption[];
      } catch {
        return [] as PromoOption[];
      }
    },
  });

  const trigger = useMutation({
    mutationFn: () =>
      api.post("/admin/campaigns/trigger", {
        campaignType,
        channel,
        promoCode: promoCode || undefined,
      }),
    onSuccess: async (res) => {
      const run = res.data.data as CampaignRun;
      setMessage(
        `Queued ${run.campaignType} for ${run.totalRecipients} users (${run.channel}). Backend job sends in batches.`,
      );
      await qc.invalidateQueries({ queryKey: ["admin-campaign-runs"] });
    },
  });

  const activePromos = (promos.data || []).filter((p) => (p.status || "").toUpperCase() === "ACTIVE");

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
      <div>
        <h2 className="text-lg font-semibold">Campaigns & alerts</h2>
        <p className="mt-1 text-sm text-slate-500">
          Queue email or WhatsApp alerts for a batch of sellers. Templates live in the database. Deactivated accounts
          are never included. WhatsApp needs an outbound API — otherwise those items are skipped.
        </p>
      </div>
      {caps.data && (
        <p className="rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-600">{caps.data.whatsappMessage}</p>
      )}
      <div className="grid gap-3 md:grid-cols-4">
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">Audience</span>
          <select
            value={campaignType}
            onChange={(e) => setCampaignType(e.target.value)}
            className="w-full rounded-lg border px-2 py-2"
          >
            <option value="NO_PURCHASE">No paid purchase yet</option>
            <option value="TRIAL_EXPIRED">Trial expired</option>
            <option value="EXPIRING_SOON">Paid plan ends in 5/4/3/1/0 days</option>
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">Channel</span>
          <select value={channel} onChange={(e) => setChannel(e.target.value)} className="w-full rounded-lg border px-2 py-2">
            <option value="EMAIL">Email</option>
            <option value="WHATSAPP">WhatsApp</option>
            <option value="BOTH">Email + WhatsApp</option>
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">Promo / coupon (optional)</span>
          <select value={promoCode} onChange={(e) => setPromoCode(e.target.value)} className="w-full rounded-lg border px-2 py-2">
            <option value="">No promo</option>
            {activePromos.map((p) => (
              <option key={p.code} value={p.code}>
                {p.code}
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-end">
          <button
            type="button"
            disabled={trigger.isPending}
            onClick={() => {
              setMessage("");
              trigger.mutate();
            }}
            className="w-full rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
          >
            {trigger.isPending ? "Queuing…" : "Trigger campaign"}
          </button>
        </div>
      </div>
      {message && <p className="text-sm text-teal-800">{message}</p>}
      {trigger.isError && <p className="text-sm text-red-600">{apiErrorMessage(trigger.error)}</p>}
      {(runs.data || []).length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-xs">
            <thead className="text-slate-500">
              <tr>
                <th className="py-2">Type</th>
                <th>Channel</th>
                <th>Promo</th>
                <th>Status</th>
                <th>Recipients</th>
                <th>Sent / skip / fail</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {(runs.data || []).slice(0, 8).map((run) => (
                <tr key={run.id} className="border-t">
                  <td className="py-2 font-medium">{run.campaignType}</td>
                  <td>{run.channel}</td>
                  <td>{run.promoCode || "—"}</td>
                  <td>{run.status}</td>
                  <td>{run.totalRecipients}</td>
                  <td>
                    {run.sentCount} / {run.skippedCount} / {run.failedCount}
                  </td>
                  <td>{new Date(run.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
