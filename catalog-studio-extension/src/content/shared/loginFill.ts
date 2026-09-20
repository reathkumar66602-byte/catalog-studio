import { isExtensionAlive, storageGet, storageSet } from "../../services/chromeAccess";

const LOGIN_TTL = 3 * 60 * 1000;

function isLogin() {
  return /(^|\/)login(\/|$)/i.test(location.pathname);
}

function fillInput(el: HTMLInputElement, value: string) {
  el.focus();
  el.value = value;
  el.dispatchEvent(new Event("input", { bubbles: true }));
  el.dispatchEvent(new Event("change", { bubbles: true }));
}

export function startLoginFill() {
  if (!isExtensionAlive() || !isLogin()) return;
  void (async () => {
    const stored = await storageGet(["csLoginE", "csLoginP", "csLoginAt"]);
    if (!stored.csLoginAt || Date.now() - Number(stored.csLoginAt) > LOGIN_TTL) {
      await storageSet({ csLoginE: "", csLoginP: "", csLoginAt: 0 });
      return;
    }
    const email = String(stored.csLoginE || "");
    const password = String(stored.csLoginP || "");
    const inputs = Array.from(document.querySelectorAll<HTMLInputElement>("input"));
    const user = inputs.find((el) => /email|mobile|phone|user/i.test(`${el.type} ${el.name} ${el.placeholder} ${el.getAttribute("aria-label") || ""}`));
    const pass = inputs.find((el) => el.type === "password");
    if (user && email) fillInput(user, email);
    if (pass && password) fillInput(pass, password);
    await storageSet({ csLoginE: "", csLoginP: "", csLoginAt: 0 });
  })();
}
