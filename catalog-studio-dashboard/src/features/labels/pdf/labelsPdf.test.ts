import { PDFDocument, StandardFonts, rgb } from "pdf-lib";
import { describe, expect, it } from "vitest";
import { cropShippingLabels, labelBoxes } from "./cropLabels";
import { cropDownloadName } from "./download";
import { mergePdfs } from "./mergePdfs";

async function samplePage(text: string, size: [number, number] = [595.28, 841.89]) {
  const doc = await PDFDocument.create();
  const page = doc.addPage(size);
  const font = await doc.embedFont(StandardFonts.Helvetica);
  page.drawText(text, { x: 40, y: size[1] - 80, size: 18, font, color: rgb(0, 0, 0) });
  page.drawText("TAX INVOICE", { x: 40, y: 80, size: 14, font, color: rgb(0.2, 0.2, 0.2) });
  return doc.save();
}

describe("download names", () => {
  it("uses catalogStudioCrop-timestamp", () => {
    expect(cropDownloadName()).toMatch(/^catalogStudioCrop-\d+\.pdf$/);
  });
});

describe("mergePdfs", () => {
  it("concatenates pages in file order", async () => {
    const a = await samplePage("A");
    const b = await samplePage("B");
    const merged = await mergePdfs([a, b]);
    const doc = await PDFDocument.load(merged);
    expect(doc.getPageCount()).toBe(2);
  });
});

describe("cropShippingLabels", () => {
  it("rotates Meesho A4 labels into a 2x2 sheet", async () => {
    const src = await PDFDocument.create();
    const font = await src.embedFont(StandardFonts.Helvetica);
    for (let i = 0; i < 4; i++) {
      const page = src.addPage([595.28, 841.89]);
      page.drawText("Customer Address", { x: 19, y: 812, size: 10, font });
      page.drawText("TAX INVOICE", { x: 266, y: 482, size: 10, font });
    }
    const cropped = await cropShippingLabels(await src.save(), { marketplace: "meesho", printer: "a4" });
    const doc = await PDFDocument.load(cropped);
    expect(doc.getPageCount()).toBe(1);
    expect(doc.getPage(0).getWidth()).toBeCloseTo(595.28, 0);
    expect(doc.getPage(0).getHeight()).toBeCloseTo(841.89, 0);
  });

  it("writes one 4x6 page per label for thermal printers", async () => {
    const a = await samplePage("Flipkart 1");
    const b = await samplePage("Flipkart 2");
    const merged = await mergePdfs([a, b]);
    const cropped = await cropShippingLabels(merged, {
      marketplace: "flipkart",
      printer: "label",
    });
    const doc = await PDFDocument.load(cropped);
    expect(doc.getPageCount()).toBe(2);
    expect(doc.getPage(0).getWidth()).toBe(288);
    expect(doc.getPage(0).getHeight()).toBe(432);
  });

  it("places Flipkart A4 labels at native size instead of stretching a half page", async () => {
    const src = await PDFDocument.create();
    const font = await src.embedFont(StandardFonts.Helvetica);
    for (let i = 0; i < 4; i++) {
      const page = src.addPage([595.28, 841.89]);
      page.drawText("E-Kart Logistics", { x: 216, y: 802, size: 10, font });
      page.drawText("Tax Invoice", { x: 52, y: 442, size: 8, font });
    }
    const cropped = await cropShippingLabels(await src.save(), { marketplace: "flipkart", printer: "a4" });
    const doc = await PDFDocument.load(cropped);
    expect(doc.getPageCount()).toBe(1);
    expect(doc.getPage(0).getWidth()).toBeCloseTo(595.28, 0);
    expect(doc.getPage(0).getHeight()).toBeCloseTo(841.89, 0);
  });

  it("uses a taller Meesho fallback than Flipkart", () => {
    const flipkart = labelBoxes(595.28, 841.89, "flipkart")[0];
    const meesho = labelBoxes(595.28, 841.89, "meesho")[0];
    expect(meesho.top - meesho.bottom).toBeGreaterThan(flipkart.top - flipkart.bottom);
  });

  it("cuts above TAX INVOICE when the heading is present", async () => {
    const bytes = await samplePage("Meesho SKU ABC123");
    const cropped = await cropShippingLabels(bytes, { marketplace: "meesho", printer: "label" });
    const doc = await PDFDocument.load(cropped);
    expect(doc.getPageCount()).toBe(1);
  });
});
