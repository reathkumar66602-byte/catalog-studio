import { existsSync, readFileSync } from "node:fs";
import { PDFDocument } from "pdf-lib";
import { describe, expect, it } from "vitest";
import { coercePdfBytes } from "./bytes";
import { cropShippingLabels } from "./cropLabels";
import { mergePdfs } from "./mergePdfs";

const DL = "C:/Users/HP/Downloads";
const meeshoFiles = [
  `${DL}/Sub_Order_Labels_b81c5723-805e-4ce2-befb-fc3d8187a436.pdf`,
  `${DL}/Sub_Order_Labels_dfe51de2-0179-4e2d-8a9e-eacd4ee3d027.pdf`,
  `${DL}/Sub_Order_Labels_c2dfbd2b-c034-4254-9ceb-f237832c1971.pdf`,
  `${DL}/Sub_Order_Labels_86885e8d-9766-4647-9487-860834282de8.pdf`,
];
const flipkartFiles = [
  `${DL}/order__1784950487245.pdf`,
  `${DL}/order__1784950456691.pdf`,
  `${DL}/order__1784950431272.pdf`,
  `${DL}/order__1784950410030.pdf`,
];
const meeshoMerge =
  [`${DL}/catalogStudioMerge-1789305905366.pdf`, `${DL}/MergedPDF.pdf`, `${DL}/MergedPDF (4).pdf`].find((file) =>
    existsSync(file),
  ) ?? "";
const meeshoQlc =
  [`${DL}/quicklabelcrop-1789306587442.pdf`, `${DL}/quicklabelcrop-1789287058414.pdf`].find((file) => existsSync(file)) ??
  "";
const hasMeesho = meeshoFiles.every((file) => existsSync(file)) && Boolean(meeshoMerge) && Boolean(meeshoQlc);
const hasFlipkart =
  flipkartFiles.every((file) => existsSync(file)) &&
  [`${DL}/MergedPDF (1).pdf`, `${DL}/MergedPDF (2).pdf`].some((file) => existsSync(file));

function loadFile(path: string) {
  return coercePdfBytes(readFileSync(path));
}

async function ysFor(bytes: Uint8Array, re: RegExp) {
  const pdfjs = await import("pdfjs-dist/legacy/build/pdf.mjs");
  const pdf = await pdfjs.getDocument({ data: bytes.slice(), isEvalSupported: false }).promise;
  const page = await pdf.getPage(1);
  const content = await page.getTextContent();
  const ys = content.items
    .filter((item) => "str" in item && re.test(item.str))
    .map((item) => ("transform" in item ? +item.transform[5].toFixed(1) : 0))
    .sort((a, b) => b - a);
  await pdf.destroy();
  return ys;
}

describe.runIf(hasMeesho)("Downloads Meesho fixtures", () => {
  it("merges four Sub_Order_Labels PDFs into four pages", async () => {
    const merged = await mergePdfs(meeshoFiles.map(loadFile));
    const doc = await PDFDocument.load(merged);
    expect(doc.getPageCount()).toBe(4);
  });

  it("A4-crops the merged Meesho PDF like Quick Label Crop", async () => {
    const cropped = await cropShippingLabels(loadFile(meeshoMerge), {
      marketplace: "meesho",
      printer: "a4",
    });
    const doc = await PDFDocument.load(cropped);
    const qlc = await PDFDocument.load(loadFile(meeshoQlc));
    expect(doc.getPageCount()).toBe(qlc.getPageCount());
    expect(doc.getPage(0).getWidth()).toBeCloseTo(595.28, 0);
    expect(doc.getPage(0).getHeight()).toBeCloseTo(841.89, 0);

    const oursInv = await ysFor(cropped, /TAX INVOICE/i);
    const qlcInv = await ysFor(loadFile(meeshoQlc), /TAX INVOICE/i);
    expect(oursInv).toHaveLength(qlcInv.length);
    expect(Math.abs(oursInv[0] - qlcInv[0])).toBeLessThan(8);
    const oursAddr = await ysFor(cropped, /^Customer Address$/);
    const qlcAddr = await ysFor(loadFile(meeshoQlc), /^Customer Address$/);
    expect(Math.abs(oursAddr[0] - qlcAddr[0])).toBeLessThan(8);
  });
});

async function xyFor(bytes: Uint8Array, re: RegExp) {
  const pdfjs = await import("pdfjs-dist/legacy/build/pdf.mjs");
  const pdf = await pdfjs.getDocument({ data: bytes.slice(), isEvalSupported: false }).promise;
  const page = await pdf.getPage(1);
  const content = await page.getTextContent();
  const points = content.items
    .filter((item) => "str" in item && re.test(item.str))
    .map((item) =>
      "transform" in item
        ? { x: +item.transform[4].toFixed(1), y: +item.transform[5].toFixed(1) }
        : { x: 0, y: 0 },
    )
    .sort((a, b) => b.y - a.y || a.x - b.x);
  await pdf.destroy();
  return points;
}

describe.runIf(hasFlipkart)("Downloads Flipkart fixtures", () => {
  it("merges four order__ PDFs into four pages", async () => {
    const merged = await mergePdfs(flipkartFiles.map(loadFile));
    const doc = await PDFDocument.load(merged);
    expect(doc.getPageCount()).toBe(4);
  });

  it("A4-crops Flipkart labels onto the same 2x2 positions as Quick Label Crop", async () => {
    const qlcPath = [
      `${DL}/quicklabelcrop-1789316721102.pdf`,
      `${DL}/quicklabelcrop-1789287469357.pdf`,
    ].find((file) => existsSync(file));
    const mergedPath = [`${DL}/MergedPDF (1).pdf`, `${DL}/MergedPDF (2).pdf`].find((file) => existsSync(file));
    expect(qlcPath).toBeTruthy();
    expect(mergedPath).toBeTruthy();
    const cropped = await cropShippingLabels(loadFile(mergedPath!), {
      marketplace: "flipkart",
      printer: "a4",
    });
    const doc = await PDFDocument.load(cropped);
    expect(doc.getPageCount()).toBe(1);
    const ours = await xyFor(cropped, /^E-Kart Logistics$/);
    const qlc = await xyFor(loadFile(qlcPath!), /^E-Kart Logistics$/);
    expect(ours.length).toBe(qlc.length);
    expect(ours.length).toBeGreaterThanOrEqual(1);
    ours.forEach((point, index) => {
      expect(Math.abs(point.x - qlc[index].x)).toBeLessThan(3);
      expect(Math.abs(point.y - qlc[index].y)).toBeLessThan(3);
    });
  });
});
