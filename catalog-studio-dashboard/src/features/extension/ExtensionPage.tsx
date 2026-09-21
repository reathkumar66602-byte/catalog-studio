import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type ReactNode } from "react";
import { api, apiErrorMessage } from "../../api/client";
import { useI18n } from "../../i18n/LanguageProvider";

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
  const { t } = useI18n();
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
      },
  });
  const [key, setKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [confirmKey, setConfirmKey] = useState(false);
  const [actionError, setActionError] = useState("");
  const [actionMessage, setActionMessage] = useState("");
  const [settings, setSettings] = useState<ExtSettings>({});
  const [keywords, setKeywords] = useState("");

  useEffect(() => {
    if (!workspace.data?.settings) return;
    setSettings(workspace.data.settings);
    setKeywords((workspace.data.settings.keywords || []).join(", "));
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
    onError: (err) => setActionError(apiErrorMessage(err, t("app.extGenFail"))),
  });
  const revoke = useMutation({
    mutationFn: (id: string) => api.post(`/extension/devices/${id}/revoke`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["devices"] }),
    onError: (err) => setActionError(apiErrorMessage(err, t("app.extRevokeFail"))),
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
      setActionMessage(t("app.extSaved"));
      await qc.invalidateQueries({ queryKey: ["extension-workspace"] });
    },
    onError: (err) => setActionError(apiErrorMessage(err, t("app.extSaveFail"))),
  });

  const devices = devicesQuery.data || [];
  const quota = workspace.data?.quota;
  const pack = settings.packaging || {};
  const price = settings.priceRule || {};

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <h1 className="text-2xl font-semibold">{t("app.extTitle")}</h1>
      <p className="text-slate-500">{t("app.extBody")}</p>
      {quota && (
        <p className="rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm text-slate-600">
          {t("app.extQuota", {
            plan: quota.plan || "—",
            used: quota.used ?? 0,
            limit: quota.limit ?? 0,
            remaining: quota.remaining ?? 0,
          })}
        </p>
      )}
      {workspace.data?.settings?.lockedShopName && (
        <p className="text-sm text-slate-600">
          {t("app.extLocked", { name: workspace.data.settings.lockedShopName })}
        </p>
      )}
      {actionError && <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{actionError}</p>}
      {actionMessage && <p className="rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{actionMessage}</p>}
      <ol className="list-decimal space-y-2 pl-5 text-sm text-slate-600">
        <li>{t("app.extStep1")}</li>
        <li>{t("app.extStep2")}</li>
        <li>{t("app.extStep3")}</li>
        <li>{t("app.extStep4")}</li>
        <li>{t("app.extStep5")}</li>
      </ol>
      {!confirmKey ? (
        <button onClick={() => setConfirmKey(true)} className="rounded-xl bg-teal-700 px-4 py-2 text-white">
          {t("app.extGenKey")}
        </button>
      ) : (
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => generate.mutate()}
            disabled={generate.isPending}
            className="rounded-xl bg-teal-700 px-4 py-2 text-white disabled:opacity-60"
          >
            {generate.isPending ? t("app.extGenerating") : t("app.extGenYes")}
          </button>
          <button type="button" onClick={() => setConfirmKey(false)} className="rounded-xl border px-4 py-2">
            {t("common.cancel")}
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
            {copied ? t("common.copied") : t("app.extCopyKey")}
          </button>
          <p className="mt-2 font-sans text-xs text-slate-400">
            {t("app.extSecret")}
          </p>
        </div>
      )}
      <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="font-medium">{t("app.extDefaults")}</h2>
        <p className="text-sm text-slate-500">{t("app.extDefaultsHint")}</p>
        <p className="text-sm text-slate-500">{t("ext.sellerPriceHint")}</p>
        {workspace.isError && (
          <p className="text-sm text-rose-700">{apiErrorMessage(workspace.error, t("app.extLoadFail"))}</p>
        )}
        <div className="grid gap-3 sm:grid-cols-3">
          <SettingField label={t("ext.inv")}>
            <input
              value={price.inventory ?? ""}
              onChange={(event) =>
                setSettings((current) => ({
                  ...current,
                  priceRule: { ...current.priceRule, inventory: Number(event.target.value) || 0 },
                }))
              }
              className="w-full rounded-xl border px-3 py-2"
            />
          </SettingField>
          <SettingField label={t("ext.mrpmul")}>
            <input
              value={price.mrpMul ?? ""}
              onChange={(event) =>
                setSettings((current) => ({
                  ...current,
                  priceRule: { ...current.priceRule, mrpMul: Number(event.target.value) || 0 },
                }))
              }
              className="w-full rounded-xl border px-3 py-2"
            />
          </SettingField>
          <SettingField label={t("ext.retcut")}>
            <input
              value={price.retCut ?? ""}
              onChange={(event) =>
                setSettings((current) => ({
                  ...current,
                  priceRule: { ...current.priceRule, retCut: Number(event.target.value) || 0 },
                }))
              }
              className="w-full rounded-xl border px-3 py-2"
            />
          </SettingField>
        </div>
        <p className="text-sm text-slate-500">{t("ext.sellerPackHint")}</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <SettingField label={t("app.extPackType")}>
            <input
              value={pack.type ?? ""}
              onChange={(event) =>
                setSettings((current) => ({ ...current, packaging: { ...current.packaging, type: event.target.value } }))
              }
              className="w-full rounded-xl border px-3 py-2"
            />
          </SettingField>
          <SettingField label={t("ext.packWt")}>
            <input
              value={pack.weight ?? ""}
              onChange={(event) =>
                setSettings((current) => ({
                  ...current,
                  packaging: { ...current.packaging, weight: event.target.value },
                }))
              }
              className="w-full rounded-xl border px-3 py-2"
            />
          </SettingField>
        </div>
        <div className="grid gap-3 sm:grid-cols-3">
          <SettingField label={t("ext.packL")}>
            <input
              value={pack.length ?? ""}
              onChange={(event) =>
                setSettings((current) => ({
                  ...current,
                  packaging: { ...current.packaging, length: event.target.value },
                }))
              }
              className="w-full rounded-xl border px-3 py-2"
            />
          </SettingField>
          <SettingField label={t("ext.packW")}>
            <input
              value={pack.width ?? ""}
              onChange={(event) =>
                setSettings((current) => ({
                  ...current,
                  packaging: { ...current.packaging, width: event.target.value },
                }))
              }
              className="w-full rounded-xl border px-3 py-2"
            />
          </SettingField>
          <SettingField label={t("ext.packH")}>
            <input
              value={pack.height ?? ""}
              onChange={(event) =>
                setSettings((current) => ({
                  ...current,
                  packaging: { ...current.packaging, height: event.target.value },
                }))
              }
              className="w-full rounded-xl border px-3 py-2"
            />
          </SettingField>
        </div>
        <SettingField label={t("ext.keywords")}>
          <input
            value={keywords}
            onChange={(event) => setKeywords(event.target.value)}
            className="w-full rounded-xl border px-3 py-2"
          />
        </SettingField>
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
          {saveSettings.isPending ? t("app.extSaving") : t("app.extSave")}
        </button>
      </section>
      <h2 className="font-medium">{t("app.extDevices")}</h2>
      {devicesQuery.isError && (
        <p className="text-sm text-rose-700">{apiErrorMessage(devicesQuery.error, t("app.extDevicesFail"))}</p>
      )}
      {devicesQuery.isLoading && <p className="text-sm text-slate-500">{t("app.extDevicesLoading")}</p>}
      {!devicesQuery.isLoading && devices.length === 0 && (
        <p className="text-sm text-slate-500">{t("app.extNoDevices")}</p>
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
                  if (window.confirm(t("app.extRevokeAsk"))) {
                    revoke.mutate(d.id);
                  }
                }}
                className="text-sm text-red-600"
              >
                {t("app.extRevoke")}
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function SettingField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block text-xs font-semibold text-slate-600">
      {label}
      <div className="mt-1 font-normal">{children}</div>
    </label>
  );
}
