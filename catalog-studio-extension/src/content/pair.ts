import { DEFAULT_API_BASE } from "../config";
import { isExtensionAlive, storageGet, storageSet } from "../services/chromeAccess";

function apiBaseFromPage() {
  if (location.port === "5173" || location.port === "4173") return DEFAULT_API_BASE;
  if (/localhost|127\.0\.0\.1/.test(location.hostname)) return `${location.protocol}//${location.hostname}:8080/api/v1`;
  return `${location.origin}/api/v1`;
}

async function pairFromSession() {
  if (!isExtensionAlive()) return;
  const access = localStorage.getItem("cs_access");
  if (!access) return;
  const stored = await storageGet(["pairingKey", "apiBase"]);
  const apiBase = stored.apiBase || apiBaseFromPage();
  try {
    const response = await fetch(`${apiBase}/extension/auto-pair`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${access}`,
        "Content-Type": "application/json",
        ...(stored.pairingKey ? { "X-Extension-Key": stored.pairingKey } : {}),
      },
      body: JSON.stringify({ deviceName: "Chrome Auto" }),
    });
    const json = await response.json();
    if (!json?.success || !json.data) return;
    const next: Record<string, unknown> = {
      apiBase,
      user: json.data.user,
      business: json.data.business || null,
    };
    const locale = localStorage.getItem("cs_locale");
    if (locale) next.csLocale = locale;
    if (json.data.pairingKey) next.pairingKey = json.data.pairingKey;
    else if (stored.pairingKey) next.pairingKey = stored.pairingKey;
    await storageSet(next);
    if (/[?&]ext_setup=1/.test(location.search)) showToast("Catalog Studio connected. Open Meesho Add Single Catalog to generate.");
  } catch {
    // dashboard visit should never break the website
  }
}

function showToast(text: string) {
  if (document.getElementById("cs-pair-toast")) return;
  const toast = document.createElement("div");
  toast.id = "cs-pair-toast";
  toast.textContent = text;
  toast.style.cssText =
    "position:fixed;top:18px;left:50%;transform:translateX(-50%);z-index:2147483647;background:#0f766e;color:#fff;font:600 14px/1.4 Segoe UI,sans-serif;padding:12px 20px;border-radius:10px;box-shadow:0 8px 24px rgba(0,0,0,.25);max-width:92vw";
  document.body.appendChild(toast);
  setTimeout(() => {
    toast.style.transition = "opacity .5s";
    toast.style.opacity = "0";
  }, 4500);
  setTimeout(() => toast.remove(), 5100);
}

function markPresence() {
  try {
    document.documentElement.setAttribute("data-cs-ext", "1");
  } catch {
    // ignore
  }
}

window.addEventListener("message", (event) => {
  if (event.source !== window || event.origin !== location.origin || !event.data) return;
  if (!isExtensionAlive()) return;
  if (event.data.type === "CS_EXT_SESSION") void pairFromSession();
  if (event.data.type === "CS_LOCALE" && typeof event.data.locale === "string") {
    void storageSet({ csLocale: event.data.locale.slice(0, 16) });
  }
});

markPresence();
if (document.readyState === "complete") {
  void pairFromSession();
} else {
  window.addEventListener("load", () => void pairFromSession(), { once: true });
}
