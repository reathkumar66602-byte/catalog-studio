import { DEFAULT_API_BASE } from "../config";
import { ignoreInvalidated } from "./chromeAccess";

export type StoredSession = {
  pairingKey: string;
  apiBase: string;
  user?: { id: string; name: string; email: string; workspace?: string };
  business?: { name?: string; address?: string; gstNumber?: string };
};

export async function getSession(): Promise<StoredSession | null> {
  return ignoreInvalidated(async () => {
    const data = await chrome.storage.local.get(["pairingKey", "apiBase", "user", "business"]);
    if (!data.pairingKey) return null;
    return {
      pairingKey: data.pairingKey,
      apiBase: data.apiBase || DEFAULT_API_BASE,
      user: data.user,
      business: data.business,
    };
  }, null);
}

export async function saveSession(session: StoredSession) {
  await ignoreInvalidated(async () => {
    await chrome.storage.local.set(session);
  }, undefined);
}

export async function clearSession() {
  await ignoreInvalidated(async () => {
    await chrome.storage.local.remove(["pairingKey", "apiBase", "user", "business", "meeshoStore"]);
  }, undefined);
}

export async function extensionFetch(path: string, init: RequestInit = {}) {
  const session = await getSession();
  if (!session) throw new Error("Extension is not paired");
  const response = await fetch(`${session.apiBase}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      "X-Extension-Key": session.pairingKey,
      ...(init.headers || {}),
    },
  });
  if (response.status === 401) {
    throw new Error("Extension key has been revoked");
  }
  const json = await response.json();
  if (!json.success) {
    throw new Error(json.message || "Request failed");
  }
  return json.data;
}
