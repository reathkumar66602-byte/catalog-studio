import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, apiErrorMessage } from "../../api/client";

type ChannelStatus = {
  channel: string;
  enabled: boolean;
  configured: boolean;
  sandbox: boolean;
  detailMasked?: string | null;
  note?: string | null;
};

type PlatformStatus = {
  source: string;
  channels: ChannelStatus[];
  guidance?: string | null;
};

type SyncResult = {
  syncType: string;
  status: string;
  itemsOk: number;
  itemsFailed: number;
  message?: string | null;
};

const SYNC_KINDS = [
  ["orders", "Sync orders"],
  ["stock", "Push stock"],
  ["labels", "Fetch labels"],
  ["full", "Full sync"],
] as const;

export function PlatformOmsPanel() {
  const qc = useQueryClient();
  const [message, setMessage] = useState("");
  const [activeChannel, setActiveChannel] = useState("AMAZON");

  const status = useQuery({
    queryKey: ["admin-oms"],
    queryFn: async () => (await api.get("/admin/oms")).data.data as PlatformStatus,
  });

  const sync = useMutation({
    mutationFn: async ({ channel, kind }: { channel: string; kind: string }) =>
      (await api.post(`/admin/oms/${channel}/sync/${kind}`)).data.data as SyncResult,
    onSuccess: async (result) => {
      setMessage(`${result.syncType}: ${result.status} — ${result.message || ""}`);
      await qc.invalidateQueries({ queryKey: ["admin-oms"] });
    },
  });

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
      <div>
        <h2 className="text-lg font-semibold">Platform OMS (Base.com-style)</h2>
        <p className="mt-1 text-sm text-slate-500">
          Credentials live in the server <strong>property / .env file</strong> (not pasted here). Super admin runs
          orders / stock / labels sync for Flipkart, Amazon, and Meesho.
        </p>
        {status.data?.guidance && <p className="mt-2 text-xs text-slate-500">{status.data.guidance}</p>}
      </div>

      <div className="grid gap-3 md:grid-cols-3">
        {(status.data?.channels || []).map((c) => (
          <button
            key={c.channel}
            type="button"
            onClick={() => setActiveChannel(c.channel)}
            className={`rounded-xl border p-3 text-left text-sm ${
              activeChannel === c.channel ? "border-teal-700 bg-teal-50" : "border-slate-200 bg-slate-50"
            }`}
          >
            <div className="font-semibold">{c.channel}</div>
            <div className="mt-1 text-xs text-slate-600">
              {c.enabled ? "Enabled" : "Disabled"} · {c.configured ? "Configured" : "Missing secrets"}
              {c.sandbox ? " · Sandbox" : ""}
            </div>
            {c.detailMasked && <div className="mt-1 text-xs text-slate-500">{c.detailMasked}</div>}
            {c.note && <div className="mt-1 text-xs text-slate-500">{c.note}</div>}
          </button>
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        {SYNC_KINDS.map(([kind, label]) => (
          <button
            key={kind}
            type="button"
            disabled={sync.isPending}
            onClick={() => {
              setMessage("");
              sync.mutate({ channel: activeChannel, kind });
            }}
            className="rounded-xl border border-teal-700 px-3 py-2 text-sm font-semibold text-teal-800 disabled:opacity-60"
          >
            {sync.isPending ? "Syncing…" : `${label} (${activeChannel})`}
          </button>
        ))}
      </div>

      {message && <p className="text-sm text-teal-800">{message}</p>}
      {sync.isError && <p className="text-sm text-red-600">{apiErrorMessage(sync.error)}</p>}

      <ol className="list-decimal space-y-1 pl-5 text-xs text-slate-500">
        <li>
          Put secrets in <code>.env</code> (local) or <code>.env.gcp</code> (prod): <code>FLIPKART_APP_*</code>,{" "}
          <code>AMAZON_LWA_*</code>, <code>MEESHO_PANEL_*</code>.
        </li>
        <li>
          Set <code>*_OMS_ENABLED=true</code> for each channel you use.
        </li>
        <li>Restart backend / redeploy Docker so properties load.</li>
        <li>Return here and run Sync orders / Push stock / Fetch labels.</li>
      </ol>
    </section>
  );
}
