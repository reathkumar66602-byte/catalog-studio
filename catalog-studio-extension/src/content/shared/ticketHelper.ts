import { isExtensionAlive, sendRuntimeMessage, storageGet, storageSet } from "../../services/chromeAccess";

const TICKET_TTL = 30 * 60 * 1000;

function isTicketPage() {
  return /support|ticket|help/i.test(location.pathname + location.search + document.title);
}

function descriptionBox() {
  const nodes = Array.from(document.querySelectorAll<HTMLTextAreaElement | HTMLInputElement>("textarea, input, [contenteditable='true']"));
  return nodes.find((el) => {
    if (el.closest("#cs-sidebar")) return false;
    const label = `${el.getAttribute("placeholder") || ""} ${el.getAttribute("aria-label") || ""} ${(el.previousElementSibling?.textContent || "")}`.toLowerCase();
    return /description|comment|message|details|tell us/i.test(label) || el instanceof HTMLTextAreaElement;
  });
}

function write(el: HTMLElement, value: string) {
  if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) {
    el.focus();
    el.value = value;
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
    return;
  }
  el.focus();
  el.textContent = value;
  el.dispatchEvent(new Event("input", { bubbles: true }));
}

export function startTicketHelper() {
  if (!isExtensionAlive()) return;
  const tryFill = async () => {
    if (!isTicketPage()) return;
    const stored = await storageGet(["csTicketMsg", "csTicketAt"]);
    const msg = typeof stored.csTicketMsg === "string" ? stored.csTicketMsg : "";
    if (!msg) return;
    if (stored.csTicketAt && Date.now() - Number(stored.csTicketAt) > TICKET_TTL) {
      await storageSet({ csTicketMsg: "", csTicketAt: 0 });
      return;
    }
    const box = descriptionBox();
    if (!box) return;
    write(box, msg);
    await storageSet({ csTicketMsg: "", csTicketAt: 0 });
    void sendRuntimeMessage({
      type: "API",
      path: "/extension/tickets",
      init: { method: "POST", body: JSON.stringify({ draft: msg }) },
    });
  };
  [800, 2000, 4000].forEach((ms) => setTimeout(() => void tryFill(), ms));
  window.addEventListener("hashchange", () => void tryFill());
}
