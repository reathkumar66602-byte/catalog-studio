import { useEffect, useState } from "react";
import { Download, X } from "lucide-react";

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
};

const DISMISS_KEY = "cs.pwaInstallDismissed";

function isIosDevice() {
  if (typeof navigator === "undefined") return false;
  const ua = navigator.userAgent;
  return /iPad|iPhone|iPod/.test(ua) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
}

function isStandalone() {
  if (typeof window === "undefined") return true;
  const media = window.matchMedia("(display-mode: standalone)").matches;
  const ios = "standalone" in navigator && Boolean((navigator as Navigator & { standalone?: boolean }).standalone);
  return media || ios;
}

/**
 * Soft prompt so mobile users can add Catalog Studio to the home screen.
 * Android/Chrome uses beforeinstallprompt; iOS shows Share → Add to Home Screen.
 */
export function PwaInstallBanner() {
  const [deferred, setDeferred] = useState<BeforeInstallPromptEvent | null>(null);
  const [showIos, setShowIos] = useState(false);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (isStandalone()) return;
    try {
      if (sessionStorage.getItem(DISMISS_KEY) === "1") return;
    } catch {
      /* ignore */
    }

    const onPrompt = (event: Event) => {
      event.preventDefault();
      setDeferred(event as BeforeInstallPromptEvent);
      setVisible(true);
    };
    window.addEventListener("beforeinstallprompt", onPrompt);

    if (isIosDevice()) {
      setShowIos(true);
      setVisible(true);
    }

    return () => window.removeEventListener("beforeinstallprompt", onPrompt);
  }, []);

  function dismiss() {
    setVisible(false);
    setDeferred(null);
    try {
      sessionStorage.setItem(DISMISS_KEY, "1");
    } catch {
      /* ignore */
    }
  }

  async function install() {
    if (!deferred) return;
    await deferred.prompt();
    await deferred.userChoice;
    dismiss();
  }

  if (!visible) return null;

  return (
    <div className="fixed inset-x-0 bottom-0 z-[60] p-3 sm:p-4 pointer-events-none">
      <div className="pointer-events-auto mx-auto flex max-w-lg items-start gap-3 rounded-2xl border border-teal-200 bg-white/95 p-3 shadow-[0_18px_40px_-20px_rgba(15,118,110,0.55)] backdrop-blur">
        <img src="/pwa-192.png" alt="" className="mt-0.5 h-11 w-11 shrink-0 rounded-xl" />
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-slate-900">Add Catalog Studio to Home Screen</p>
          <p className="mt-0.5 text-xs leading-relaxed text-slate-500">
            {showIos && !deferred
              ? "Tap Share, then Add to Home Screen for a one-tap app icon."
              : "Install the app for quick access like a native app on your phone."}
          </p>
          <div className="mt-2 flex flex-wrap items-center gap-2">
            {deferred && (
              <button
                type="button"
                onClick={() => void install()}
                className="inline-flex items-center gap-1.5 rounded-lg bg-teal-700 px-3 py-1.5 text-xs font-semibold text-white hover:bg-teal-800"
              >
                <Download size={14} />
                Install
              </button>
            )}
            <button type="button" onClick={dismiss} className="text-xs font-medium text-slate-500 hover:text-slate-700">
              Not now
            </button>
          </div>
        </div>
        <button type="button" aria-label="Dismiss" onClick={dismiss} className="rounded-lg p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600">
          <X size={16} />
        </button>
      </div>
    </div>
  );
}
