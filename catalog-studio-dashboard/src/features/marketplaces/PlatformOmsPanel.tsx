import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, apiErrorMessage } from "../../api/client";

type ChannelStatus = {
  channel: string;
  enabled: boolean;
  configured: boolean;
  sandbox: boolean;
};

type PlatformStatus = {
  channels: ChannelStatus[];
};

type SyncResult = {
  syncType: string;
  status: string;
  message?: string | null;
};

const CHANNEL_COPY: Record<string, { title: string; detail: string }> = {
  FLIPKART: { title: "Flipkart", detail: "Orders, stock, and shipping labels" },
  AMAZON: { title: "Amazon", detail: "Orders, stock, and shipping labels" },
  MEESHO: { title: "Meesho", detail: "Orders, stock, and shipping labels" },
};

const SYNC_KINDS = [
  ["orders", "Sync orders"],
  ["stock", "Update stock"],
  ["labels", "Get labels"],
  ["full", "Sync everything"],
] as const;

function channelTitle(code: string) {
  return CHANNEL_COPY[code]?.title || code;
}

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
      setMessage(`${channelTitle(activeChannel)}: ${result.message || result.status}`);
      await qc.invalidateQueries({ queryKey: ["admin-oms"] });
    },
  });

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
      <div>
        <h2 className="text-lg font-semibold">Order sync</h2>
        <p className="mt-1 text-sm text-slate-500">
          Keep Flipkart, Amazon, and Meesho orders, stock, and shipping labels in one place.
        </p>
      </div>

      <div className="grid gap-3 md:grid-cols-3">
        {(status.data?.channels || []).map((channel) => {
          const connected = channel.enabled && channel.configured;
          const copy = CHANNEL_COPY[channel.channel];
          const selected = activeChannel === channel.channel;
          return (
            <button
              key={channel.channel}
              type="button"
              onClick={() => setActiveChannel(channel.channel)}
              className={`rounded-xl border p-4 text-left ${
                selected ? "border-teal-700 bg-teal-50" : "border-slate-200 bg-white"
              }`}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="font-semibold">{copy?.title || channel.channel}</span>
                <span
                  className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                    connected ? "bg-emerald-100 text-emerald-800" : "bg-slate-100 text-slate-600"
                  }`}
                >
                  {connected ? (channel.sandbox ? "Sandbox" : "Connected") : "Not connected"}
                </span>
              </div>
              <p className="mt-2 text-sm text-slate-500">{copy?.detail || "Orders, stock, and labels"}</p>
            </button>
          );
        })}
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
            {sync.isPending ? "Syncing…" : `${label}`}
          </button>
        ))}
      </div>
      <p className="text-xs text-slate-500">Actions run for {channelTitle(activeChannel)}.</p>

      {message && <p className="text-sm text-teal-800">{message}</p>}
      {sync.isError && <p className="text-sm text-red-600">{apiErrorMessage(sync.error)}</p>}
    </section>
  );
}
