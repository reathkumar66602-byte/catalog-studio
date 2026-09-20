import { visible } from "./fieldFinder";

export type PageImageSource = "page-upload" | "page-scan" | "recapture";

const SKIP_SRC = /logo|icon|avatar|placeholder|sprite|favicon|data:image\/svg/i;

export function pickBestPageImage(): HTMLImageElement | null {
  const images = Array.from(document.querySelectorAll("img")).filter(isLikelyProductPhoto);
  if (!images.length) return null;
  return images.sort((a, b) => scoreImage(b) - scoreImage(a))[0] || null;
}

export async function imageElementToFile(img: HTMLImageElement): Promise<File | null> {
  const src = img.currentSrc || img.src;
  if (!src || SKIP_SRC.test(src)) return null;
  try {
    const response = await fetch(src);
    const blob = await response.blob();
    if (blob.size < 400) return null;
    const type = blob.type && blob.type.startsWith("image/") ? blob.type : "image/jpeg";
    return new File([blob], "meesho-front.jpg", { type });
  } catch {
    return canvasToFile(img);
  }
}

export async function captureBestPageImage(): Promise<File | null> {
  const img = pickBestPageImage();
  if (!img) return null;
  if (!img.complete) {
    await new Promise<void>((resolve) => {
      img.addEventListener("load", () => resolve(), { once: true });
      img.addEventListener("error", () => resolve(), { once: true });
      setTimeout(resolve, 1200);
    });
  }
  return imageElementToFile(img);
}

export function watchMeeshoPageImages(onFile: (file: File, source: PageImageSource) => void): () => void {
  const onChange = (event: Event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement) || target.type !== "file") return;
    if (target.closest("#cs-sidebar")) return;
    const file = Array.from(target.files || []).find((item) => item.type.startsWith("image/"));
    if (file) onFile(file, "page-upload");
  };
  document.addEventListener("change", onChange, true);

  let lastSrc = "";
  const scan = async () => {
    const img = pickBestPageImage();
    if (!img) return;
    const src = img.currentSrc || img.src;
    if (!src || src === lastSrc) return;
    const file = await imageElementToFile(img);
    if (!file) return;
    lastSrc = src;
    onFile(file, "page-scan");
  };

  let scanTimer = 0;
  const observer = new MutationObserver((mutations) => {
    if (mutations.every((mutation) => {
      const el = mutation.target.nodeType === Node.ELEMENT_NODE
        ? mutation.target as Element
        : mutation.target.parentElement;
      return Boolean(el?.closest("#cs-sidebar, #cs-fab"));
    })) return;
    if (scanTimer) return;
    scanTimer = window.setTimeout(() => {
      scanTimer = 0;
      void scan();
    }, 400);
  });
  observer.observe(document.documentElement, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ["src"],
  });
  void scan();
  const interval = window.setInterval(() => void scan(), 2500);

  return () => {
    document.removeEventListener("change", onChange, true);
    observer.disconnect();
    window.clearInterval(interval);
  };
}

function isLikelyProductPhoto(img: HTMLImageElement) {
  if (img.closest("#cs-sidebar") || img.id === "cs-image") return false;
  if (!visible(img)) return false;
  const src = img.currentSrc || img.src || "";
  if (!src || SKIP_SRC.test(src)) return false;
  const rect = img.getBoundingClientRect();
  const w = img.naturalWidth || rect.width;
  const h = img.naturalHeight || rect.height;
  if (w > 0 && h > 0 && (w < 48 || h < 48)) return false;
  return true;
}

function nearbyText(img: HTMLImageElement) {
  let host: HTMLElement | null = img.parentElement;
  for (let i = 0; i < 5 && host; i += 1) {
    const text = (host.innerText || "").trim();
    if (text && text.length < 90) {
      return `${text} ${img.alt || ""}`.toLowerCase();
    }
    host = host.parentElement;
  }
  return (img.alt || "").toLowerCase();
}

function scoreImage(img: HTMLImageElement) {
  let score = 0;
  const nearby = nearbyText(img);
  const rect = img.getBoundingClientRect();
  if (/product 1|product\s*1|selected product/.test(nearby)) score += 100;
  if (rect.top < 240 && rect.width >= 48 && rect.width <= 150) score += 70;
  if (/front view|upload front|front image/.test(nearby)) score += 45;
  if (/\bfront\b/.test(nearby)) score += 15;
  const src = img.currentSrc || img.src || "";
  if (src.startsWith("blob:") || src.startsWith("data:image")) score += 25;
  score += Math.min(rect.width || img.naturalWidth || 0, 360) / 18;
  return score;
}

function canvasToFile(img: HTMLImageElement): Promise<File | null> {
  return new Promise((resolve) => {
    try {
      const canvas = document.createElement("canvas");
      canvas.width = img.naturalWidth || Math.round(img.getBoundingClientRect().width);
      canvas.height = img.naturalHeight || Math.round(img.getBoundingClientRect().height);
      if (canvas.width < 48 || canvas.height < 48) {
        resolve(null);
        return;
      }
      const ctx = canvas.getContext("2d");
      if (!ctx) {
        resolve(null);
        return;
      }
      ctx.drawImage(img, 0, 0);
      canvas.toBlob(
        (blob) => resolve(blob ? new File([blob], "meesho-front.jpg", { type: "image/jpeg" }) : null),
        "image/jpeg",
        0.92,
      );
    } catch {
      resolve(null);
    }
  });
}
