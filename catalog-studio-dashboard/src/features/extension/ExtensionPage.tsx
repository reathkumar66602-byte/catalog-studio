import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { api, apiErrorMessage } from "../../api/client";

type Device = { id: string; deviceName: string; status: string; lastActiveAt?: string };

type PriceRule = { retCut?: number; mrpMul?: number; inventory?: number };
type Packaging = { type?: string; length?: string; width?: string; height?: string; weight?: string };
type ExtSettings = {
  priceRule?: PriceRule;
  packaging?: Packaging;
  keywords?: string[];
  lockedShopName?: string;
  lockedShopUid?: string;
};
type Quota = { plan?: string; used?: number; limit?: number; remaining?: number; requiresRecharge?: boolean };

export function ExtensionPage() {
  const qc = useQueryClient();
  const devicesQuery = useQuery({
    queryKey: ["devices"],
    queryFn: async () => (await api.get("/extension/devices")).data.data as Device[],
  });
  const workspace = useQuery({
    queryKey: ["extension-workspace"],
    queryFn: async () =>
      (await api.get("/extension/workspace")).data.data as {
        settings: ExtSettings;
        quota: Quota;
        lastTicket?: { draft?: string; ticketNo?: string };
      },
  });
  const [key, setKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [ticket, setTicket] = useState("");
  const [loginE, setLoginE] = useState("");
  const [loginP, setLoginP] = useState("");
  const [confirmKey, setConfirmKey] = useState(false);
  const [actionError, setActionError] = useState("");
  const [actionMessage, setActionMessage] = useState("");
  const [settings, setSettings] = useState<ExtSettings>({});
  const [keywords, setKeywords] = useState("");

  useEffect(() => {
    if (!workspace.data?.settings) return;
    setSettings(workspace.data.settings);
    setKeywords((workspace.data.settings.keywords || []).join(", "));
    if (workspace.data.lastTicket?.draft) {
      setTicket((current) => current || workspace.data.lastTicket?.draft || "");
    }
  }, [workspace.data]);

  const generate = useMutation({
    mutationFn: () => api.post("/extension/keys", null, { params: { deviceName: "Chrome" } }),
    onSuccess: (res) => {
      setKey(res.data.data.pairingKey);
      setCopied(false);
      setConfirmKey(false);
      setActionError("");
      qc.invalidateQueries({ queryKey: ["devices"] });
    },
    onError: (err) => setActionError(apiErrorMessage(err, "Could not generate pairing key")),
  });
  const revoke = useMutation({
    mutationFn: (id: string) => api.post(`/extension/devices/${id}/revoke`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["devices"] }),
    onError: (err) => setActionError(apiErrorMessage(err, "Could not revoke device")),
  });
  const saveSettings = useMutation({
    mutationFn: () =>
      api.put("/extension/workspace/settings", {
        priceRule: settings.priceRule,
        packaging: settings.packaging,
        keywords: keywords
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean),
      }),
    onSuccess: async () => {
      setActionMessage("Extension settings saved");
      await qc.invalidateQueries({ queryKey: ["extension-workspace"] });
    },
    onError: (err) => setActionError(apiErrorMessage(err, "Could not save extension settings")),
  });

  const devices = devicesQuery.data || [];
  const quota = workspace.data?.quota;
  const pack = settings.packaging || {};
  const price = settings.priceRule || {};

  async function armTicket() {
    setActionError("");
    setActionMessage("");
    const draft = ticket.slice(0, 4000);
    try {
      await api.post("/extension/workspace/tickets", { draft });
      window.postMessage({ type: "CS_TICKET_MSG", msg: draft }, window.location.origin);
      setActionMessage("Ticket draft saved and armed in this browser for 30 minutes.");
    } catch (err) {
      setActionError(apiErrorMessage(err, "Could not save ticket draft"));
    }
  }

  function armLogin() {
    window.postMessage({ type: "CS_LOGIN_FILL", e: loginE, p: loginP }, window.location.origin);
    setLoginP("");
    setActionMessage("Meesho login is armed in this browser for 3 minutes. Catalog Studio never stores that password.");
    window.open("https://supplier.meesho.com/login", "_blank", "noopener,noreferrer");
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <h1 className="text-2xl font-semibold">Chrome extension</h1>
      <p className="text-slate-500">
        Pair Catalog Studio Autofill with this workspace. Stay logged in here and the extension pairs itself — no key
        paste needed. On Meesho it reads the shop name, then fills GST, HSN, manufacturer, packer, and product fields.
        It never submits the listing for you.
      </p>
      {quota && (
        <p className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600">
          Plan {quota.plan || "—"} · AI used {quota.used ?? 0}/{quota.limit ?? 0} this month · {quota.remaining ?? 0}{" "}
          remaining
        </p>
      )}
      {workspace.data?.settings?.lockedShopName && (
        <p className="text-sm text-slate-600">
          Locked Meesho shop: <span className="font-medium">{workspace.data.settings.lockedShopName}</span>
        </p>
      )}
      {actionError && <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{actionError}</p>}
      {actionMessage && <p className="rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{actionMessage}</p>}
      <ol className="list-decimal space-y-2 pl-5 text-sm text-slate-600">
        <li>
          Load the unpacked extension from <code>catalog-studio-extension/dist</code>.
        </li>
        <li>Keep this dashboard tab open while logged in. Pairing uses your short-lived JWT access token, then stores a hashed pairing key in the extension.</li>
        <li>Optional fallback: generate a key and paste it in the extension options page.</li>
        <li>Open Meesho Add Single Catalog. Click the Catalog Studio toolbar icon to toggle the sidebar.</li>
        <li>Add the Front View photo, click Generate, review titles, then Fill Values for Form.</li>
      </ol>
      {!confirmKey ? (
        <button onClick={() => setConfirmKey(true)} className="rounded-xl bg-teal-700 px-4 py-2 text-white">
          Generate pairing key
        </button>
      ) : (
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => generate.mutate()}
            disabled={generate.isPending}
            className="rounded-xl bg-teal-700 px-4 py-2 text-white disabled:opacity-60"
          >
            {generate.isPending ? "Generating..." : "Yes, create a new secret key"}
          </button>
          <button type="button" onClick={() => setConfirmKey(false)} className="rounded-xl border px-4 py-2">
            Cancel
          </button>
        </div>
      )}
      {key && (
        <div className="rounded-2xl bg-slate-900 p-4 font-mono text-sm text-emerald-300">
          <p className="break-all">{key}</p>
          <button
            type="button"
            className="mt-3 rounded-lg bg-emerald-400 px-3 py-1 font-sans text-xs font-medium text-slate-900"
            onClick={async () => {
              await navigator.clipboard.writeText(key);
              setCopied(true);
            }}
          >
            {copied ? "Copied" : "Copy key"}
          </button>
          <p className="mt-2 font-sans text-xs text-slate-400">
            Treat this like a password. It is stored hashed on the server and will not be shown again.
          </p>
        </div>
      )}
      <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="font-medium">Listing defaults</h2>
        <p className="text-sm text-slate-500">Saved to your Catalog Studio account and used by the paired extension.</p>
        {workspace.isError && (
          <p className="text-sm text-rose-700">{apiErrorMessage(workspace.error, "Could not load extension settings")}</p>
        )}
        <div className="grid gap-3 sm:grid-cols-3">
          <input
            value={price.inventory ?? ""}
            onChange={(event) =>
              setSettings((current) => ({
                ...current,
                priceRule: { ...current.priceRule, inventory: Number(event.target.value) || 0 },
              }))
            }
            className="rounded-xl border px-3 py-2"
            placeholder="Inventory"
          />
          <input
            value={price.mrpMul ?? ""}
            onChange={(event) =>
              setSettings((current) => ({
                ...current,
                priceRule: { ...current.priceRule, mrpMul: Number(event.target.value) || 0 },
              }))
            }
            className="rounded-xl border px-3 py-2"
            placeholder="MRP multiplier"
          />
          <input
            value={price.retCut ?? ""}
            onChange={(event) =>
              setSettings((current) => ({
                ...current,
                priceRule: { ...current.priceRule, retCut: Number(event.target.value) || 0 },
              }))
            }
            className="rounded-xl border px-3 py-2"
            placeholder="Retail cut"
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <input
            value={pack.type ?? ""}
            onChange={(event) =>
              setSettings((current) => ({ ...current, packaging: { ...current.packaging, type: event.target.value } }))
            }
            className="rounded-xl border px-3 py-2"
            placeholder="Packaging type"
          />
          <input
            value={pack.weight ?? ""}
            onChange={(event) =>
              setSettings((current) => ({
                ...current,
                packaging: { ...current.packaging, weight: event.target.value },
              }))
            }
            className="rounded-xl border px-3 py-2"
            placeholder="Weight (g)"
          />
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <input
            value={pack.length ?? ""}
            onChange={(event) =>
              setSettings((current) => ({
                ...current,
                packaging: { ...current.packaging, length: event.target.value },
              }))
            }
            className="rounded-xl border px-3 py-2"
            placeholder="Length"
          />
          <input
            value={pack.width ?? ""}
            onChange={(event) =>
              setSettings((current) => ({
                ...current,
                packaging: { ...current.packaging, width: event.target.value },
              }))
            }
            className="rounded-xl border px-3 py-2"
            placeholder="Width"
          />
          <input
            value={pack.height ?? ""}
            onChange={(event) =>
              setSettings((current) => ({
                ...current,
                packaging: { ...current.packaging, height: event.target.value },
              }))
            }
            className="rounded-xl border px-3 py-2"
            placeholder="Height"
          />
        </div>
        <input
          value={keywords}
          onChange={(event) => setKeywords(event.target.value)}
          className="w-full rounded-xl border px-3 py-2"
          placeholder="Keywords, comma separated"
        />
        <button
          type="button"
          disabled={saveSettings.isPending}
          onClick={() => {
            setActionError("");
            setActionMessage("");
            saveSettings.mutate();
          }}
          className="rounded-xl bg-teal-700 px-4 py-2 text-white disabled:opacity-60"
        >
          {saveSettings.isPending ? "Saving..." : "Save extension settings"}
        </button>
      </section>
      <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="font-medium">Support ticket helper</h2>
        <p className="text-sm text-slate-500">
          Draft is saved to your account and stays in this browser for 30 minutes to fill Meesho&apos;s Description box.
          Catalog Studio never submits the ticket.
        </p>
        <textarea
          value={ticket}
          onChange={(e) => setTicket(e.target.value)}
          rows={4}
          className="w-full rounded-xl border px-3 py-2"
          placeholder="Ticket description"
        />
        <button type="button" className="rounded-xl bg-teal-700 px-4 py-2 text-white" onClick={armTicket}>
          Arm ticket fill
        </button>
      </section>
      <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="font-medium">Meesho login fill</h2>
        <p className="text-sm text-slate-500">
          Meesho credentials stay in this browser tab only. They are posted to the extension with{" "}
          <code>window.postMessage</code> on this origin and expire in 3 minutes. Catalog Studio never sends this
          password to the API.
        </p>
        <input
          value={loginE}
          onChange={(e) => setLoginE(e.target.value)}
          autoComplete="off"
          className="w-full rounded-xl border px-3 py-2"
          placeholder="Meesho email or mobile"
        />
        <input
          value={loginP}
          onChange={(e) => setLoginP(e.target.value)}
          type="password"
          autoComplete="new-password"
          className="w-full rounded-xl border px-3 py-2"
          placeholder="Meesho password"
        />
        <button type="button" className="rounded-xl bg-teal-700 px-4 py-2 text-white" onClick={armLogin}>
          Arm and open Meesho login
        </button>
      </section>
      <h2 className="font-medium">Connected devices</h2>
      {devicesQuery.isError && (
        <p className="text-sm text-rose-700">{apiErrorMessage(devicesQuery.error, "Could not load devices")}</p>
      )}
      {devicesQuery.isLoading && <p className="text-sm text-slate-500">Loading paired devices...</p>}
      {!devicesQuery.isLoading && devices.length === 0 && (
        <p className="text-sm text-slate-500">No paired devices yet. Keep this tab open with the extension loaded.</p>
      )}
      <div className="space-y-2">
        {devices.map((d) => (
          <div key={d.id} className="flex items-center justify-between rounded-xl border border-slate-200 bg-white px-4 py-3">
            <div>
              <p className="font-medium">{d.deviceName}</p>
              <p className="text-xs text-slate-500">
                {d.status}
                {d.lastActiveAt ? ` · ${new Date(d.lastActiveAt).toLocaleString("en-IN")}` : ""}
              </p>
            </div>
            {d.status === "ACTIVE" && (
              <button
                onClick={() => {
                  if (window.confirm("Revoke this device? The extension will need to pair again.")) {
                    revoke.mutate(d.id);
                  }
                }}
                className="text-sm text-red-600"
              >
                Revoke
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
