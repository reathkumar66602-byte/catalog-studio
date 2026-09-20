import { DEFAULT_API_BASE, DEFAULT_DASHBOARD_URL, SETUP_URL } from "../config";

chrome.runtime.onInstalled.addListener((details) => {
  if (details.reason === "install") {
    chrome.storage.local.set({
      apiBase: DEFAULT_API_BASE,
      dashboardUrl: DEFAULT_DASHBOARD_URL,
    });
    chrome.tabs.create({ url: SETUP_URL }).catch(() => undefined);
  }
  if (details.reason === "update") healToken(false);
});

try {
  chrome.runtime.onStartup.addListener(() => healToken(false));
} catch {
  // ignore
}

chrome.action.onClicked.addListener(async (tab) => {
  if (!tab?.id) return;
  const url = tab.url || "";
  const stored = await chrome.storage.local.get(["pairingKey", "dashboardUrl"]);
  if (!stored.pairingKey) {
    await chrome.tabs.create({ url: `${stored.dashboardUrl || DEFAULT_DASHBOARD_URL}/extension?ext_setup=1` });
    return;
  }
  if (/supplier\.meesho\.com/i.test(url)) {
    const inventory = /\/inventory(\/|\?|#|$)/.test(url);
    await toggle(tab.id, inventory ? "CS_TOGGLE_IMGSYNC" : "CS_TOGGLE", inventory ? "imgsync.js" : "content.js");
    if (inventory) await toggle(tab.id, "CS_TOGGLE", "content.js");
    return;
  }
  if (/meesho\.com/i.test(url)) {
    await toggle(tab.id, "CS_TOGGLE_SCOUT", "scout.js");
  }
});

chrome.runtime.onConnect.addListener((port) => {
  if (port.name !== "cs-fill") return;
  const started = Date.now();
  const timer = setInterval(() => {
    if (Date.now() - started > 20 * 60 * 1000) {
      clearInterval(timer);
      try {
        port.disconnect();
      } catch {
        // ignore
      }
      return;
    }
    try {
      port.postMessage({ type: "CS_TICK" });
      chrome.tabs.sendMessage(port.sender?.tab?.id || -1, { type: "CS_TICK" }).catch(() => undefined);
    } catch {
      clearInterval(timer);
    }
  }, 25);
  port.onDisconnect.addListener(() => clearInterval(timer));
  port.onMessage.addListener((msg) => {
    if (msg?.type === "CS_TICK" && port.sender?.tab?.id) {
      chrome.scripting
        .executeScript({
          target: { tabId: port.sender.tab.id },
          world: "MAIN",
          func: () => window.postMessage({ type: "CS_TICK" }, location.origin),
        })
        .catch(() => undefined);
    }
  });
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type === "CS_SLEEP") {
    setTimeout(() => sendResponse({ ok: true }), Math.min(Number(message.ms) || 0, 20000));
    return true;
  }
  if (message?.type === "IMG_GRAB") {
    grabImage(String(message.url || ""))
      .then(sendResponse)
      .catch((error) => sendResponse({ ok: false, error: error instanceof Error ? error.message : "Image read failed" }));
    return true;
  }
  if (message?.type === "IMG_PUSH") {
    pushPhoto(message)
      .then(sendResponse)
      .catch((error) => sendResponse({ error: error instanceof Error ? error.message : "Photo sync failed" }));
    return true;
  }
  if (message.type === "API") {
    handleApi(message.path, message.init)
      .then(sendResponse)
      .catch((error) => sendResponse({ error: error instanceof Error ? error.message : "Request failed" }));
    return true;
  }
  if (message.type === "PAIR") {
    handlePair(message.apiBase, message.pairingKey, message.deviceName)
      .then(sendResponse)
      .catch((error) => sendResponse({ error: error instanceof Error ? error.message : "Pairing failed" }));
    return true;
  }
  if (message.type === "UNPAIR") {
    handleUnpair()
      .then(sendResponse)
      .catch((error) => sendResponse({ error: error instanceof Error ? error.message : "Disconnect failed" }));
    return true;
  }
  if (message.type === "ANALYZE") {
    handleAnalyze(message)
      .then(sendResponse)
      .catch((error) => sendResponse({ error: error instanceof Error ? error.message : "Analyze failed" }));
    return true;
  }
});

async function toggle(tabId: number, type: string, file: string) {
  try {
    await chrome.tabs.sendMessage(tabId, { type });
  } catch {
    try {
      await chrome.scripting.executeScript({ target: { tabId }, files: [file] });
      await new Promise((r) => setTimeout(r, 300));
      await chrome.tabs.sendMessage(tabId, { type });
    } catch {
      // page not injectable
    }
  }
}

async function healToken(openTab: boolean) {
  const stored = await chrome.storage.local.get(["pairingKey", "apiBase", "dashboardUrl"]);
  if (!stored.pairingKey) return;
  try {
    const response = await fetch(`${stored.apiBase || DEFAULT_API_BASE}/extension/ping`, {
      headers: { "X-Extension-Key": stored.pairingKey },
    });
    if (response.status !== 401) return;
    await chrome.storage.local.remove(["pairingKey", "user", "business", "meeshoStore"]);
    if (openTab) {
      await chrome.tabs.create({ url: `${stored.dashboardUrl || DEFAULT_DASHBOARD_URL}/extension?ext_setup=1` });
    }
  } catch {
    // offline — keep the key
  }
}

async function grabImage(url: string) {
  if (!/^https:\/\/images\.meesho\.com\//i.test(url) && !/^https:\/\/.*meesho\.com\//i.test(url)) {
    return { ok: false, error: "blocked" };
  }
  const response = await fetch(url, { credentials: "omit", referrerPolicy: "no-referrer", cache: "force-cache" });
  const blob = await response.blob();
  const buffer = await blob.arrayBuffer();
  const bytes = new Uint8Array(buffer);
  let binary = "";
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return { ok: true, base64: btoa(binary), contentType: blob.type || "image/jpeg" };
}

async function pushPhoto(message: { sourceId?: string; contentType?: string; base64?: string }) {
  const session = await chrome.storage.local.get(["pairingKey", "apiBase"]);
  if (!session.pairingKey) throw new Error("Extension is not paired");
  const binary = atob(message.base64 || "");
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  const form = new FormData();
  form.append("sourceId", message.sourceId || "photo");
  form.append("thumb", new Blob([bytes], { type: message.contentType || "image/jpeg" }), "thumb.jpg");
  const response = await fetch(`${session.apiBase}/extension/photos`, {
    method: "POST",
    headers: { "X-Extension-Key": session.pairingKey },
    body: form,
  });
  const json = await response.json();
  if (response.status === 401) await clearPairing();
  if (!json.success) throw new Error(json.message || "Photo sync failed");
  return json;
}

async function handlePair(apiBase: string, pairingKey: string, deviceName: string) {
  const key = (pairingKey || "").trim();
  if (!key) {
    throw new Error("Paste a pairing key from the dashboard first");
  }
  const base = (apiBase || DEFAULT_API_BASE).replace(/\/$/, "");
  const response = await fetch(`${base}/extension/pair`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Extension-Key": key,
    },
    body: JSON.stringify({ pairingKey: key, deviceName: deviceName || "Chrome" }),
  });
  const json = await response.json();
  if (!json.success) {
    throw new Error(json.message || "Pairing failed");
  }
  await chrome.storage.local.set({ pairingKey: key, apiBase: base, user: json.data.user, business: json.data.business || null });
  return json;
}

async function handleUnpair() {
  const stored = await chrome.storage.local.get(["pairingKey", "apiBase"]);
  if (stored.pairingKey && stored.apiBase) {
    await fetch(`${stored.apiBase}/extension/unpair`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pairingKey: stored.pairingKey, deviceName: "Chrome" }),
    });
  }
  await clearPairing();
  return { success: true };
}

async function handleAnalyze(message: {
  filename?: string;
  contentType?: string;
  base64?: string;
  marketplace?: string;
  notes?: string;
  categoryHint?: string;
  meeshoName?: string;
  meeshoUid?: string;
}) {
  const session = await chrome.storage.local.get(["pairingKey", "apiBase"]);
  if (!session.pairingKey) {
    throw new Error("Extension is not paired");
  }
  if (!message.base64) {
    throw new Error("Choose a product image first");
  }
  const binary = atob(message.base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  const form = new FormData();
  form.append(
    "images",
    new Blob([bytes], { type: message.contentType || "image/jpeg" }),
    message.filename || "product.jpg",
  );
  form.append("marketplace", message.marketplace || "MEESHO");
  if (message.notes) form.append("productTypeHint", message.notes);
  if (message.categoryHint) form.append("categoryHint", message.categoryHint);
  if (message.meeshoName) form.append("meeshoName", message.meeshoName);
  if (message.meeshoUid) form.append("meeshoUid", message.meeshoUid);
  const response = await fetch(`${session.apiBase}/extension/analyze`, {
    method: "POST",
    headers: { "X-Extension-Key": session.pairingKey },
    body: form,
  });
  const json = await response.json();
  if (response.status === 401) {
    await clearPairing();
    throw new Error("Extension key has been revoked");
  }
  if (!json.success) {
    throw new Error(json.message || "Analyze failed");
  }
  return json;
}

async function handleApi(path: string, init: RequestInit = {}) {
  const session = await chrome.storage.local.get(["pairingKey", "apiBase"]);
  if (!session.pairingKey) {
    throw new Error("Extension is not paired");
  }
  const response = await fetch(`${session.apiBase}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-Extension-Key": session.pairingKey,
      ...(init.headers as Record<string, string> | undefined),
    },
  });
  const json = await response.json();
  if (response.status === 401) {
    await clearPairing();
    throw new Error("Extension key has been revoked");
  }
  if (!json.success) {
    throw new Error(json.message || "Request failed");
  }
  return json;
}

async function clearPairing() {
  await chrome.storage.local.remove(["pairingKey", "user", "business", "meeshoStore"]);
}
