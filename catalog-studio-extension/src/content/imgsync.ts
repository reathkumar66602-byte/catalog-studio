import { isExtensionAlive, sendRuntimeMessage } from "../services/chromeAccess";
import { getSession } from "../services/storage";

function isInventory() {
  return /\/inventory(\/|\?|#|$)/i.test(location.pathname + location.search);
}

function sourceId(url: string, fallback: string) {
  const match = url.match(/(\d{6,})/);
  return match ? match[1] : fallback.slice(0, 40);
}

function visibleImages() {
  return Array.from(document.querySelectorAll<HTMLImageElement>("img")).filter((img) => {
    if (img.closest("#cs-imgsync, #cs-sidebar, #cs-fab")) return false;
    const src = img.currentSrc || img.src || "";
    if (!/images\.meesho\.com|meesho\.com/i.test(src)) return false;
    const r = img.getBoundingClientRect();
    return r.width >= 48 && r.height >= 48 && r.bottom > 0 && r.top < innerHeight;
  });
}

function ensureUi() {
  if (document.getElementById("cs-imgsync")) return document.getElementById("cs-imgsync") as HTMLElement;
  const ui = document.createElement("div");
  ui.id = "cs-imgsync";
  ui.style.cssText =
    "display:none;position:fixed;left:14px;bottom:14px;z-index:2147483000;align-items:center;gap:8px;background:#fff;border:1px solid #d6ddf4;border-radius:12px;padding:8px 10px;box-shadow:0 6px 22px rgba(20,22,55,.18);font:12px Segoe UI,sans-serif";
  ui.innerHTML = `<button id="cs-is-btn" style="border:0;background:#0f766e;color:#fff;font-weight:700;padding:7px 12px;border-radius:9px;cursor:pointer">Capture photos on this page</button>
    <span id="cs-is-st" style="color:#64748b"></span>
    <button id="cs-is-x" style="border:0;background:transparent;cursor:pointer">×</button>`;
  document.documentElement.appendChild(ui);
  ui.querySelector("#cs-is-x")?.addEventListener("click", () => {
    ui.style.display = "none";
  });
  ui.querySelector("#cs-is-btn")?.addEventListener("click", () => void collect());
  return ui;
}

async function collect() {
  const st = document.getElementById("cs-is-st");
  const session = await getSession();
  if (!session) {
    if (st) st.textContent = "Pair Catalog Studio first.";
    return;
  }
  const images = visibleImages();
  if (!images.length) {
    if (st) st.textContent = "No product photos visible on this page.";
    return;
  }
  let saved = 0;
  for (const [index, img] of images.entries()) {
    const url = img.currentSrc || img.src;
    const grabbed = await sendRuntimeMessage<{ ok?: boolean; base64?: string; contentType?: string }>({
      type: "IMG_GRAB",
      url,
    });
    if (!grabbed?.ok || !grabbed.base64) continue;
    const result = await sendRuntimeMessage<{ error?: string }>({
      type: "IMG_PUSH",
      sourceId: sourceId(url, `img-${index}`),
      contentType: grabbed.contentType || "image/webp",
      base64: grabbed.base64,
    });
    if (!result?.error) saved += 1;
    if (st) st.textContent = `Saved ${saved} / ${images.length}`;
  }
  if (st) st.textContent = `Saved ${saved} thumbnail${saved === 1 ? "" : "s"} from this page.`;
}

function toggle() {
  if (!isInventory()) return;
  const ui = ensureUi();
  ui.style.display = ui.style.display === "flex" ? "none" : "flex";
}

if (isExtensionAlive()) {
  chrome.runtime.onMessage.addListener((msg) => {
    if (msg?.type === "CS_TOGGLE_IMGSYNC") toggle();
  });
}
