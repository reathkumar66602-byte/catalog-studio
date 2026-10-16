import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, apiErrorMessage } from "../../api/client";

type AmazonConnection = {
  id?: string | null;
  status: string;
  accountLabel?: string | null;
  clientIdMasked?: string | null;
  marketplaceId?: string | null;
  secretConfigured: boolean;
  refreshTokenConfigured: boolean;
  sandbox: boolean;
  syncNote?: string | null;
};

export function AmazonSpApiPanel() {
  const qc = useQueryClient();
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [refreshToken, setRefreshToken] = useState("");
  const [marketplaceId, setMarketplaceId] = useState("A21TJRUUN4KGV");
  const [accountLabel, setAccountLabel] = useState("Amazon main");
  const [sandbox, setSandbox] = useState(true);
  const [message, setMessage] = useState("");

  const status = useQuery({
    queryKey: ["amazon-sp-api"],
    queryFn: async () => (await api.get("/marketplaces/amazon/sp-api")).data.data as AmazonConnection,
  });

  const connect = useMutation({
    mutationFn: () =>
      api.post("/marketplaces/amazon/sp-api/connect", {
        clientId,
        clientSecret,
        refreshToken,
        marketplaceId: marketplaceId || undefined,
        accountLabel: accountLabel || undefined,
        sandbox,
      }),
    onSuccess: async () => {
      setMessage("Amazon SP-API credentials saved. Sync stays off until the SP-API client is turned on.");
      setClientSecret("");
      setRefreshToken("");
      await qc.invalidateQueries({ queryKey: ["amazon-sp-api"] });
      await qc.invalidateQueries({ queryKey: ["marketplaces"] });
    },
  });

  const disconnect = useMutation({
    mutationFn: () => api.delete("/marketplaces/amazon/sp-api"),
    onSuccess: async () => {
      setMessage("Amazon SP-API disconnected.");
      await qc.invalidateQueries({ queryKey: ["amazon-sp-api"] });
      await qc.invalidateQueries({ queryKey: ["marketplaces"] });
    },
  });

  const s = status.data;

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
      <div>
        <h2 className="text-lg font-semibold">Amazon SP-API (OMS)</h2>
        <p className="mt-1 text-sm text-slate-500">
          Not your Seller Central password. Create a <strong>private</strong> app under Apps &amp; Services → Develop
          Apps, then paste <strong>LWA Client ID</strong>, <strong>Client Secret</strong>, and <strong>Refresh Token</strong>{" "}
          (after you click Authorize). India marketplace id defaults to <code>A21TJRUUN4KGV</code>.
        </p>
      </div>

      {s && (
        <div className="rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-600">
          Status: <strong>{s.status}</strong>
          {s.clientIdMasked ? ` · Client ${s.clientIdMasked}` : ""}
          {s.marketplaceId ? ` · Marketplace ${s.marketplaceId}` : ""}
          {s.secretConfigured ? " · Secret saved" : ""}
          {s.refreshTokenConfigured ? " · Refresh token saved" : ""}
          {s.sandbox ? " · Sandbox" : " · Production"}
          {s.syncNote ? ` — ${s.syncNote}` : ""}
        </div>
      )}

      <div className="grid gap-3 md:grid-cols-2">
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">Account label</span>
          <input
            value={accountLabel}
            onChange={(e) => setAccountLabel(e.target.value)}
            className="w-full rounded-lg border px-3 py-2"
          />
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">Marketplace ID (Amazon.in)</span>
          <input
            value={marketplaceId}
            onChange={(e) => setMarketplaceId(e.target.value)}
            className="w-full rounded-lg border px-3 py-2"
          />
        </label>
        <label className="text-sm md:col-span-2">
          <span className="mb-1 block text-slate-500">LWA Client ID</span>
          <input
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            className="w-full rounded-lg border px-3 py-2"
            autoComplete="off"
          />
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">LWA Client Secret</span>
          <input
            type="password"
            value={clientSecret}
            onChange={(e) => setClientSecret(e.target.value)}
            className="w-full rounded-lg border px-3 py-2"
            autoComplete="new-password"
          />
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">Refresh Token</span>
          <input
            type="password"
            value={refreshToken}
            onChange={(e) => setRefreshToken(e.target.value)}
            className="w-full rounded-lg border px-3 py-2"
            autoComplete="new-password"
          />
        </label>
      </div>

      <label className="flex items-center gap-2 text-sm text-slate-600">
        <input type="checkbox" checked={sandbox} onChange={(e) => setSandbox(e.target.checked)} />
        Sandbox app (uncheck when using production credentials)
      </label>

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          disabled={connect.isPending || !clientId.trim() || !clientSecret.trim() || !refreshToken.trim()}
          onClick={() => {
            setMessage("");
            connect.mutate();
          }}
          className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          {connect.isPending ? "Saving…" : "Save Amazon SP-API credentials"}
        </button>
        {s?.status === "CONNECTED" && (
          <button
            type="button"
            disabled={disconnect.isPending}
            onClick={() => {
              setMessage("");
              disconnect.mutate();
            }}
            className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700"
          >
            Disconnect
          </button>
        )}
      </div>

      {message && <p className="text-sm text-teal-800">{message}</p>}
      {(connect.isError || disconnect.isError) && (
        <p className="text-sm text-red-600">{apiErrorMessage(connect.error || disconnect.error)}</p>
      )}

      <ol className="list-decimal space-y-1 pl-5 text-xs text-slate-500">
        <li>Seller Central (India) → Apps &amp; Services → Develop Apps.</li>
        <li>Complete developer profile if asked, then Add a new app (private / for your own account).</li>
        <li>Open the app → View LWA credentials → copy Client ID + Client Secret.</li>
        <li>Click Authorize app → copy the Refresh Token.</li>
        <li>Paste all three into this form and save. Keep Sandbox checked until you move to production.</li>
      </ol>
    </section>
  );
}
