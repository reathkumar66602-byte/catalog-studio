import { useEffect } from "react";
import { useAuth } from "../../store/auth";
import { LOCALE_STORAGE_KEY } from "../../i18n/catalog";

export function ExtensionBridge() {
  const { user } = useAuth();

  useEffect(() => {
    document.documentElement.setAttribute("data-cs-ext-page", user ? "1" : "0");
    const locale = localStorage.getItem(LOCALE_STORAGE_KEY) || "en";
    window.postMessage({ type: "CS_LOCALE", locale }, window.location.origin);
    if (!user) return;
    window.postMessage({ type: "CS_EXT_SESSION" }, window.location.origin);
  }, [user]);

  return null;
}
