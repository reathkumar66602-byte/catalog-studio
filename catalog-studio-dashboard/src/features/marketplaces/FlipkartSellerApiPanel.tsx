import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, apiErrorMessage } from "../../api/client";

type SellerConnection = {
  id?: string | null;
  status: string;
  accountLabel?: string | null;
  appIdMasked?: string | null;
  locationId?: string | null;
  secretConfigured: boolean;
  syncNote?: string | null;
};

export function FlipkartSellerApiPanel() {
  const qc = useQueryClient();
  const [appId, setAppId] = useState("");
  const [appSecret, setAppSecret] = useState("");
  const [locationId, setLocationId] = useState("");
  const [accountLabel, setAccountLabel] = useState("Flipkart main");
  const [message, setMessage] = useState("");

  const status = useQuery({
    queryKey: ["flipkart-seller-api"],
    queryFn: async () => (await api.get("/marketplaces/flipkart/seller-api")).data.data as SellerConnection,
  });

  const connect = useMutation({
    mutationFn: () =>
      api.post("/marketplaces/flipkart/seller-api/connect", {
        appId,
        appSecret,
        locationId: locationId || undefined,
        accountLabel: accountLabel || undefined,
      }),
    onSuccess: async () => {
      setMessage("Credentials saved in Catalog Studio. Stock sync stays off until Flipkart API client is turned on.");
      setAppSecret("");
      await qc.invalidateQueries({ queryKey: ["flipkart-seller-api"] });
      await qc.invalidateQueries({ queryKey: ["marketplaces"] });
    },
  });

  const disconnect = useMutation({
    mutationFn: () => api.delete("/marketplaces/flipkart/seller-api"),
    onSuccess: async () => {
      setMessage("Flipkart Seller API disconnected.");
      await qc.invalidateQueries({ queryKey: ["flipkart-seller-api"] });
      await qc.invalidateQueries({ queryKey: ["marketplaces"] });
    },
  });

  const s = status.data;

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
      <div>
        <h2 className="text-lg font-semibold">Flipkart Seller API (OMS)</h2>
        <p className="mt-1 text-sm text-slate-500">
          This is <strong>not</strong> your Flipkart seller login password. Create an API app in Flipkart Seller Hub /
          developer API settings, then paste <strong>App ID</strong> and <strong>Secret</strong> here. Location ID is
          your warehouse/fulfillment location from Flipkart.
        </p>
      </div>

      {s && (
        <div className="rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-600">
          Status: <strong>{s.status}</strong>
          {s.appIdMasked ? ` · App ID ${s.appIdMasked}` : ""}
          {s.locationId ? ` · Location ${s.locationId}` : ""}
          {s.secretConfigured ? " · Secret saved" : ""}
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
          <span className="mb-1 block text-slate-500">Location ID (optional for now)</span>
          <input
            value={locationId}
            onChange={(e) => setLocationId(e.target.value)}
            className="w-full rounded-lg border px-3 py-2"
            placeholder="From Flipkart locations list"
          />
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">App ID</span>
          <input
            value={appId}
            onChange={(e) => setAppId(e.target.value)}
            className="w-full rounded-lg border px-3 py-2"
            autoComplete="off"
          />
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">App Secret</span>
          <input
            type="password"
            value={appSecret}
            onChange={(e) => setAppSecret(e.target.value)}
            className="w-full rounded-lg border px-3 py-2"
            autoComplete="new-password"
          />
        </label>
      </div>

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          disabled={connect.isPending || !appId.trim() || !appSecret.trim()}
          onClick={() => {
            setMessage("");
            connect.mutate();
          }}
          className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          {connect.isPending ? "Saving…" : "Save Flipkart API credentials"}
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
        <li>Log in to Flipkart Seller Hub with your supplier account.</li>
        <li>Open Seller API / developer / integrations (wording varies) and create an application.</li>
        <li>Copy App ID + Secret into the form above and save.</li>
        <li>Download / note your Location ID when Flipkart shows warehouse locations.</li>
        <li>After we enable sync, Catalog Studio stock will push to Flipkart from Inventory.</li>
      </ol>
    </section>
  );
}
