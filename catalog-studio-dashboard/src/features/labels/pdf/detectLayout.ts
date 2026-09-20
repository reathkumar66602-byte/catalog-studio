import { regionsFromText, type LabelMarketplace, type LabelRegion, type TextItem } from "./layoutMath";
import { coercePdfBytes } from "./bytes";

export async function detectLabelRegions(
  fileBytes: ArrayBuffer | Uint8Array,
  marketplace: LabelMarketplace,
): Promise<LabelRegion[][] | null> {
  try {
    const pdfjs = await import("pdfjs-dist");
    pdfjs.GlobalWorkerOptions.workerSrc = new URL(
      "pdfjs-dist/build/pdf.worker.min.mjs",
      import.meta.url,
    ).toString();
    const pdf = await pdfjs.getDocument({
      data: coercePdfBytes(fileBytes).slice(),
      isEvalSupported: false,
      disableAutoFetch: true,
      disableStream: true,
    }).promise;
    try {
      const pages: LabelRegion[][] = [];
      for (let i = 1; i <= pdf.numPages; i++) {
        const page = await pdf.getPage(i);
        const viewport = page.getViewport({ scale: 1 });
        const content = await page.getTextContent();
        const items: TextItem[] = [];
        for (const item of content.items) {
          if (!("str" in item) || !item.str?.trim()) continue;
          items.push({
            str: item.str,
            x: item.transform[4],
            y: item.transform[5],
            fontSize: Math.abs(item.transform[3]) || 10,
          });
        }
        pages.push(regionsFromText(items, viewport.width, viewport.height, marketplace));
      }
      return pages;
    } finally {
      await pdf.destroy().catch(() => undefined);
    }
  } catch {
    return null;
  }
}
