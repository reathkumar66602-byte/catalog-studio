declare global {
  interface Window {
    Cashfree?: (options: { mode: "sandbox" | "production" }) => {
      checkout: (options: {
        paymentSessionId: string;
        redirectTarget?: "_self" | "_blank" | "_top" | "_modal";
      }) => Promise<unknown>;
    };
  }
}

const SDK_SRC = "https://sdk.cashfree.com/js/v3/cashfree.js";

function loadSdk(): Promise<void> {
  if (window.Cashfree) {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${SDK_SRC}"]`);
    if (existing) {
      existing.addEventListener("load", () => resolve());
      existing.addEventListener("error", () => reject(new Error("Cashfree SDK failed to load")));
      return;
    }
    const script = document.createElement("script");
    script.src = SDK_SRC;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Cashfree SDK failed to load"));
    document.head.appendChild(script);
  });
}

export async function openCashfreeCheckout(paymentSessionId: string, mode: "sandbox" | "production" = "production") {
  if (!paymentSessionId) {
    throw new Error("Missing Cashfree payment session");
  }
  await loadSdk();
  if (!window.Cashfree) {
    throw new Error("Cashfree SDK unavailable");
  }
  const cashfree = window.Cashfree({ mode });
  await cashfree.checkout({
    paymentSessionId,
    redirectTarget: "_self",
  });
}
