import { useEffect, useMemo, useState } from "react";
import { DEFAULT_API_BASE } from "../config";
import { LOCALES, normalizeLocale, translate, type LocaleCode } from "../i18n/catalog";

const LOGO = `data:image/svg+xml;utf8,${encodeURIComponent(
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="16" fill="#0F766E"/><rect x="20" y="13" width="26" height="34" rx="5" fill="#5EEAD4" opacity="0.38"/><rect x="17" y="16" width="26" height="34" rx="5" fill="#99F6E4" opacity="0.7"/><rect x="13" y="20" width="28" height="30" rx="6" fill="#FFFFFF"/><rect x="18" y="26" width="11" height="3.2" rx="1.6" fill="#0F766E"/><rect x="18" y="32.5" width="18" height="2.4" rx="1.2" fill="#5EEAD4"/><rect x="18" y="38" width="14" height="2.4" rx="1.2" fill="#99F6E4"/><path d="M49 14.5l1.85 4.05L55 20.4l-4.15 1.85L49 26.3l-1.85-4.05L43 20.4l4.15-1.85L49 14.5z" fill="#FDE68A"/></svg>`,
)}`;

export function Popup() {
  const [apiBase, setApiBase] = useState(DEFAULT_API_BASE);
  const [key, setKey] = useState("");
  const [statusKey, setStatusKey] = useState<"popup.notPaired" | "popup.connected" | "popup.pairing" | "custom">("popup.notPaired");
  const [statusCustom, setStatusCustom] = useState("");
  const [user, setUser] = useState("");
  const [store, setStore] = useState("");
  const [locale, setLocale] = useState<LocaleCode>("en");

  const t = useMemo(() => (keyName: string) => translate(locale, keyName), [locale]);
  const statusLabel =
    statusKey === "custom" ? statusCustom : t(statusKey);

  useEffect(() => {
    chrome.storage.local.get(["pairingKey", "apiBase", "user", "meeshoStore", "csLocale"]).then((data) => {
      if (data.apiBase) setApiBase(data.apiBase);
      if (data.csLocale) setLocale(normalizeLocale(String(data.csLocale)));
      if (data.user) {
        setUser(data.user.name);
        setStatusKey("popup.connected");
      }
      if (data.meeshoStore?.name) setStore(data.meeshoStore.name);
    });
  }, []);

  function changeLocale(next: string) {
    const code = normalizeLocale(next);
    setLocale(code);
    void chrome.storage.local.set({ csLocale: code });
  }

  async function pair() {
    const pairingKey = key.trim();
    if (!pairingKey.startsWith("cst_")) {
      setStatusKey("custom");
      setStatusCustom(t("popup.invalidKey"));
      return;
    }
    setStatusKey("popup.pairing");
    try {
      const response = await chrome.runtime.sendMessage({
        type: "PAIR",
        apiBase: apiBase.trim(),
        pairingKey,
        deviceName: "Chrome",
      });
      if (response?.error || !response?.success) {
        setStatusKey("custom");
        setStatusCustom(response?.error || response?.message || t("popup.failed"));
        return;
      }
      setUser(response.data.user.name);
      setStatusKey("popup.connected");
    } catch (error) {
      setStatusKey("custom");
      setStatusCustom(error instanceof Error ? error.message : t("popup.failed"));
    }
  }

  async function disconnect() {
    await chrome.runtime.sendMessage({ type: "UNPAIR" });
    setUser("");
    setKey("");
    setStore("");
    setStatusKey("popup.notPaired");
  }

  return (
    <div style={{ padding: 16, width: 320, fontFamily: "Segoe UI, sans-serif", direction: locale === "ur" ? "rtl" : "ltr" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12 }}>
        <img src={LOGO} width={36} height={36} alt="Catalog Studio" />
        <div style={{ flex: 1 }}>
          <h2 style={{ margin: 0, fontSize: 16 }}>Catalog Studio</h2>
          <p style={{ margin: 0, color: "#64748b", fontSize: 12 }}>{t("popup.tagline")} v1.4.0</p>
        </div>
      </div>
      <label style={{ display: "block", fontSize: 12, marginBottom: 10 }}>
        {t("lang.label")}
        <select
          value={locale}
          onChange={(event) => changeLocale(event.target.value)}
          style={{ width: "100%", marginTop: 4, padding: "6px 8px", borderRadius: 8, border: "1px solid #cbd5e1" }}
        >
          {LOCALES.map((item) => (
            <option key={item.code} value={item.code}>
              {item.native}
            </option>
          ))}
        </select>
      </label>
      <p style={{ color: "#64748b", fontSize: 13 }}>{t("popup.help")}</p>
      <p style={{ fontSize: 13 }}>
        {t("popup.status")}: <strong style={{ color: statusKey === "popup.connected" ? "#15803d" : undefined }}>{statusLabel}</strong>{" "}
        {user && `· ${user}`}
      </p>
      {store && (
        <p style={{ fontSize: 13, color: "#065f46", background: "#ecfdf5", borderRadius: 999, padding: "6px 10px" }}>
          ✓ {t("popup.store")}: {store}
        </p>
      )}
      <label style={{ fontSize: 12 }}>
        {t("popup.apiBase")}
        <input value={apiBase} onChange={(e) => setApiBase(e.target.value)} style={{ width: "100%", marginTop: 4 }} />
      </label>
      <label style={{ fontSize: 12, display: "block", marginTop: 8 }}>
        {t("popup.pairingKey")}
        <input
          value={key}
          onChange={(e) => setKey(e.target.value)}
          style={{ width: "100%", marginTop: 4 }}
          placeholder={t("popup.placeholder")}
          autoComplete="off"
        />
      </label>
      <button onClick={pair} style={{ marginTop: 12, width: "100%", background: "#0f766e", color: "#fff", border: 0, padding: 10, borderRadius: 8 }}>
        {t("popup.pair")}
      </button>
      <button onClick={disconnect} style={{ marginTop: 8, width: "100%", padding: 10, borderRadius: 8 }}>
        {t("popup.disconnect")}
      </button>
    </div>
  );
}
