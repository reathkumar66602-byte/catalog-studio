import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, apiErrorMessage } from "../../api/client";

type MeeshoConnection = {
  id?: string | null;
  status: string;
  accountLabel?: string | null;
  emailMasked?: string | null;
  passwordConfigured: boolean;
  syncEnabled: boolean;
  liveAuthVerified: boolean;
  syncNote?: string | null;
  lastOrdersSyncAt?: string | null;
  lastStockSyncAt?: string | null;
  lastLabelsSyncAt?: string | null;
  pendingOrders: number;
};

type SyncResult = {
  runId: string;
  syncType: string;
  status: string;
  itemsOk: number;
  itemsFailed: number;
  message?: string | null;
};

type ChannelOrder = {
  id: string;
  externalOrderId: string;
  status: string;
  sku?: string | null;
  quantity: number;
  buyerName?: string | null;
  shippingCity?: string | null;
  awb?: string | null;
  labelStatus: string;
  labelUrl?: string | null;
};

export function MeeshoPanelConnectPanel() {
  const qc = useQueryClient();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [accountLabel, setAccountLabel] = useState("Meesho main");
  const [message, setMessage] = useState("");

  const status = useQuery({
    queryKey: ["meesho-panel"],
    queryFn: async () => (await api.get("/marketplaces/meesho/panel")).data.data as MeeshoConnection,
  });

  const orders = useQuery({
    queryKey: ["meesho-panel-orders"],
    queryFn: async () => (await api.get("/marketplaces/meesho/panel/orders")).data.data as ChannelOrder[],
    enabled: status.data?.status === "CONNECTED",
  });

  const connect = useMutation({
    mutationFn: () =>
      api.post("/marketplaces/meesho/panel/connect", {
        email,
        password,
        accountLabel: accountLabel || undefined,
      }),
    onSuccess: async (res) => {
      const note = (res.data?.data as MeeshoConnection | undefined)?.syncNote;
      setMessage(note || "Meesho Supplier Panel connected. You can sync orders, stock, and labels.");
      setPassword("");
      await qc.invalidateQueries({ queryKey: ["meesho-panel"] });
      await qc.invalidateQueries({ queryKey: ["marketplaces"] });
    },
  });

  const disconnect = useMutation({
    mutationFn: () => api.delete("/marketplaces/meesho/panel"),
    onSuccess: async () => {
      setMessage("Meesho Supplier Panel disconnected.");
      await qc.invalidateQueries({ queryKey: ["meesho-panel"] });
      await qc.invalidateQueries({ queryKey: ["meesho-panel-orders"] });
      await qc.invalidateQueries({ queryKey: ["marketplaces"] });
    },
  });

  const runSync = useMutation({
    mutationFn: async (kind: "orders" | "stock" | "labels" | "full") =>
      (await api.post(`/marketplaces/meesho/panel/sync/${kind}`)).data.data as SyncResult,
    onSuccess: async (result) => {
      setMessage(`${result.syncType}: ${result.status} — ${result.message || ""}`);
      await qc.invalidateQueries({ queryKey: ["meesho-panel"] });
      await qc.invalidateQueries({ queryKey: ["meesho-panel-orders"] });
    },
  });

  const s = status.data;
  const connected = s?.status === "CONNECTED";

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
      <div>
        <h2 className="text-lg font-semibold">Meesho Supplier Panel (OMS)</h2>
        <p className="mt-1 text-sm text-slate-500">
          Same flow as Base.com: connect with your <strong>supplier.meesho.com</strong> email + password, then sync
          orders, push Catalog Studio stock, and fetch shipping labels. This is <strong>not</strong> a Meesho partner
          App ID.
        </p>
      </div>

      {s && (
        <div className="rounded-xl bg-slate-50 px-3 py-2 text-xs text-slate-600">
          Status: <strong>{s.status}</strong>
          {s.emailMasked ? ` · ${s.emailMasked}` : ""}
          {s.passwordConfigured ? " · Password saved" : ""}
          {s.liveAuthVerified ? " · Live login verified" : " · Live login pending"}
          {typeof s.pendingOrders === "number" ? ` · Pending orders ${s.pendingOrders}` : ""}
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
          <span className="mb-1 block text-slate-500">Supplier Panel email</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full rounded-lg border px-3 py-2"
            autoComplete="username"
            placeholder="seller@example.com"
          />
        </label>
        <label className="text-sm md:col-span-2">
          <span className="mb-1 block text-slate-500">Supplier Panel password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded-lg border px-3 py-2"
            autoComplete="current-password"
          />
        </label>
      </div>

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          disabled={connect.isPending || !email.trim() || !password.trim()}
          onClick={() => {
            setMessage("");
            connect.mutate();
          }}
          className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          {connect.isPending ? "Connecting…" : "Connect Meesho panel"}
        </button>
        {connected && (
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

      {connected && (
        <div className="space-y-3 border-t border-slate-100 pt-4">
          <p className="text-sm font-medium text-slate-700">Sync (CS inventory is source of truth)</p>
          <div className="flex flex-wrap gap-2">
            {(
              [
                ["orders", "Sync orders"],
                ["stock", "Push stock"],
                ["labels", "Fetch labels"],
                ["full", "Full sync"],
              ] as const
            ).map(([kind, label]) => (
              <button
                key={kind}
                type="button"
                disabled={runSync.isPending}
                onClick={() => {
                  setMessage("");
                  runSync.mutate(kind);
                }}
                className="rounded-xl border border-teal-700 px-3 py-2 text-sm font-semibold text-teal-800 disabled:opacity-60"
              >
                {runSync.isPending ? "Syncing…" : label}
              </button>
            ))}
          </div>
          <div className="grid gap-2 text-xs text-slate-500 md:grid-cols-3">
            <div>Orders sync: {s?.lastOrdersSyncAt ? new Date(s.lastOrdersSyncAt).toLocaleString() : "—"}</div>
            <div>Stock sync: {s?.lastStockSyncAt ? new Date(s.lastStockSyncAt).toLocaleString() : "—"}</div>
            <div>Labels sync: {s?.lastLabelsSyncAt ? new Date(s.lastLabelsSyncAt).toLocaleString() : "—"}</div>
          </div>

          {(orders.data || []).length > 0 && (
            <div className="overflow-x-auto rounded-xl border border-slate-100">
              <table className="min-w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    <th className="px-3 py-2">Order</th>
                    <th className="px-3 py-2">SKU</th>
                    <th className="px-3 py-2">Qty</th>
                    <th className="px-3 py-2">Status</th>
                    <th className="px-3 py-2">Label</th>
                  </tr>
                </thead>
                <tbody>
                  {(orders.data || []).slice(0, 20).map((o) => (
                    <tr key={o.id} className="border-t border-slate-100">
                      <td className="px-3 py-2 font-medium">{o.externalOrderId}</td>
                      <td className="px-3 py-2">{o.sku || "—"}</td>
                      <td className="px-3 py-2">{o.quantity}</td>
                      <td className="px-3 py-2">{o.status}</td>
                      <td className="px-3 py-2">
                        {o.labelUrl ? (
                          <a href={o.labelUrl} target="_blank" rel="noreferrer" className="text-teal-700 underline">
                            {o.labelStatus}
                          </a>
                        ) : (
                          o.labelStatus
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {message && <p className="text-sm text-teal-800">{message}</p>}
      {(connect.isError || disconnect.isError || runSync.isError) && (
        <p className="text-sm text-red-600">
          {apiErrorMessage(connect.error || disconnect.error || runSync.error)}
        </p>
      )}

      <ol className="list-decimal space-y-1 pl-5 text-xs text-slate-500">
        <li>Use the exact email/password for supplier.meesho.com (not Flipkart/Amazon keys).</li>
        <li>If you change the Meesho password later, reconnect here or sync will fail.</li>
        <li>Keep stock accurate under Inventory — Push stock sends available qty to Meesho.</li>
        <li>Sync orders, then Fetch labels for AWB / PDF when Meesho session routes are live.</li>
      </ol>
    </section>
  );
}
