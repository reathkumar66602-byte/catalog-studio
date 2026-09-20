export function cropDownloadName() {
  return `catalogStudioCrop-${Date.now()}.pdf`;
}

export function mergeDownloadName() {
  return `catalogStudioMerge-${Date.now()}.pdf`;
}

function clickAnchor(anchor: HTMLAnchorElement) {
  anchor.dispatchEvent(new MouseEvent("click", { bubbles: true, cancelable: true, view: window }));
}

/**
 * Same pattern Quick Label Crop uses: Blob + <a download>, so Chrome puts the
 * file on the download shelf instead of opening a Save As dialog.
 */
export function downloadPdf(bytes: Uint8Array, filename: string) {
  const copy = new Uint8Array(bytes);
  const nav = window.navigator as Navigator & {
    msSaveOrOpenBlob?: (blob: Blob, name: string) => void;
  };
  const blob = new Blob([copy], { type: "application/octet-stream" });
  if (typeof nav.msSaveOrOpenBlob === "function") {
    nav.msSaveOrOpenBlob(blob, filename);
    return;
  }
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = filename;
  link.rel = "noopener";
  link.style.display = "none";
  document.body.appendChild(link);
  clickAnchor(link);
  window.setTimeout(() => {
    URL.revokeObjectURL(href);
    link.remove();
  }, 2_000);
}
