import { isExtensionAlive } from "../../services/chromeAccess";

let port: chrome.runtime.Port | null = null;

export function startFillSession() {
  stopFillSession();
  try {
    window.postMessage({ type: "CS_AWAKE", on: true }, location.origin);
    if (!isExtensionAlive()) return;
    port = chrome.runtime.connect({ name: "cs-fill" });
    port.onMessage.addListener((msg) => {
      if (msg?.type === "CS_TICK") window.postMessage({ type: "CS_TICK" }, location.origin);
    });
  } catch {
    // fill still runs in the foreground tab
  }
}

export function stopFillSession() {
  try {
    port?.disconnect();
  } catch {
    // ignore
  }
  port = null;
  try {
    window.postMessage({ type: "CS_AWAKE", on: false }, location.origin);
  } catch {
    // ignore
  }
}
