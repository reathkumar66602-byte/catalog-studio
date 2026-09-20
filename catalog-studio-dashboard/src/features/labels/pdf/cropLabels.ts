import {
  PDFDocument,
  StandardFonts,
  clip,
  degrees,
  endPath,
  popGraphicsState,
  pushGraphicsState,
  rectangle,
  rgb,
  type PDFEmbeddedPage,
  type PDFPage,
} from "pdf-lib";
import { detectLabelRegions } from "./detectLayout";
import { coercePdfBytes } from "./bytes";
import { fallbackBoxes, flipkartSlipBox, type Box, type LabelMarketplace, type LabelRegion } from "./layoutMath";

export type PrinterMode = "label" | "a4";
export type { LabelMarketplace };

const A4: [number, number] = [595.28, 841.89];
const THERMAL_4X6: [number, number] = [288, 432];

function near(value: number, target: number, ratio = 0.1) {
  return Math.abs(value - target) / target <= ratio;
}

function isThermalPage(width: number, height: number) {
  const short = Math.min(width, height);
  const long = Math.max(width, height);
  return short >= 240 && short <= 330 && long >= 370 && long <= 500;
}

function isA4Page(width: number, height: number) {
  const short = Math.min(width, height);
  const long = Math.max(width, height);
  return near(short, A4[0], 0.12) && near(long, A4[1], 0.12);
}

export function labelBoxes(width: number, height: number, marketplace: LabelMarketplace): Box[] {
  if (isThermalPage(width, height)) {
    return [{ left: 0, bottom: 0, right: width, top: height }];
  }
  if (isA4Page(width, height) || height > width) {
    return fallbackBoxes(width, height, marketplace);
  }
  return [{ left: 0, bottom: 0, right: width, top: height }];
}

function clampBox(box: Box, width: number, height: number): Box {
  return {
    left: Math.max(0, Math.min(box.left, width - 8)),
    bottom: Math.max(0, Math.min(box.bottom, height - 8)),
    right: Math.max(8, Math.min(box.right, width)),
    top: Math.max(8, Math.min(box.top, height)),
  };
}

async function embedRegion(output: PDFDocument, page: PDFPage, region: LabelRegion) {
  const { width, height } = page.getSize();
  const box = clampBox(region.box, width, height);
  try {
    return await output.embedPage(page, box);
  } catch {
    return output.embedPage(page);
  }
}

function a4Cell() {
  return { w: A4[0] / 2, h: A4[1] / 2 };
}

function sourceBox(
  region: LabelRegion,
  width: number,
  height: number,
  marketplace: LabelMarketplace,
  alreadyFourUp: boolean,
): Box {
  if (alreadyFourUp) return region.box;
  if (marketplace === "flipkart") return flipkartSlipBox(width, height);
  return region.box;
}

function clipCell(sheet: PDFPage, x: number, y: number, w: number, h: number) {
  sheet.pushOperators(pushGraphicsState(), rectangle(x, y, w, h), clip(), endPath());
}

function unclip(sheet: PDFPage) {
  sheet.pushOperators(popGraphicsState());
}

function drawSku(
  page: PDFPage,
  sku: string | undefined,
  x: number,
  y: number,
  width: number,
  font: Awaited<ReturnType<PDFDocument["embedFont"]>>,
) {
  if (!sku) return;
  const label = sku.slice(0, 36);
  page.drawRectangle({
    x,
    y: y + 2,
    width,
    height: 14,
    color: rgb(1, 1, 1),
    opacity: 0.92,
  });
  page.drawText(label, {
    x: x + 4,
    y: y + 6,
    size: 8,
    font,
    color: rgb(0.1, 0.1, 0.1),
  });
}

type PlacedLabel = { embed: PDFEmbeddedPage; sku?: string; rotate90: boolean };

function placeThermal(
  sheet: PDFPage,
  label: PlacedLabel,
  font: Awaited<ReturnType<PDFDocument["embedFont"]>>,
) {
  const pad = 4;
  const cellW = THERMAL_4X6[0] - pad * 2;
  const cellH = THERMAL_4X6[1] - pad * 2;
  const scale = Math.min(cellW / label.embed.width, cellH / label.embed.height);
  const width = label.embed.width * scale;
  const height = label.embed.height * scale;
  const x = pad + (cellW - width) / 2;
  const y = pad + (cellH - height) / 2;
  sheet.drawPage(label.embed, { x, y, width, height });
  drawSku(sheet, label.sku, x, y, width, font);
}

function placeA4Cell(
  sheet: PDFPage,
  label: PlacedLabel,
  index: number,
  rotate90: boolean,
) {
  const { w: cellW, h: cellH } = a4Cell();
  const col = index % 2;
  const row = Math.floor(index / 2);
  const cellX = col * cellW;
  const cellY = A4[1] - (row + 1) * cellH;
  clipCell(sheet, cellX, cellY, cellW, cellH);
  if (rotate90) {
    const inset = 10;
    const scale = (cellW - inset * 2) / label.embed.height;
    const visW = label.embed.height * scale;
    const visH = label.embed.width * scale;
    sheet.drawPage(label.embed, {
      x: cellX + inset + visW,
      y: cellY + (cellH - visH) / 2,
      xScale: scale,
      yScale: scale,
      rotate: degrees(90),
    });
  } else {
    const padX = 20;
    const padY = 30;
    if (label.embed.width <= cellW - padX && label.embed.height <= cellH - padY) {
      sheet.drawPage(label.embed, {
        x: cellX + padX,
        y: cellY + padY,
        width: label.embed.width,
        height: label.embed.height,
      });
    } else {
      const scale = Math.min((cellW - 16) / label.embed.width, (cellH - 16) / label.embed.height);
      const width = label.embed.width * scale;
      const height = label.embed.height * scale;
      sheet.drawPage(label.embed, {
        x: cellX + (cellW - width) / 2,
        y: cellY + (cellH - height) / 2,
        width,
        height,
      });
    }
  }
  unclip(sheet);
}

export async function cropShippingLabels(
  fileBytes: ArrayBuffer | Uint8Array,
  options: { marketplace: LabelMarketplace; printer: PrinterMode },
): Promise<Uint8Array> {
  const source = await PDFDocument.load(coercePdfBytes(fileBytes), { ignoreEncryption: true });
  const detected = await detectLabelRegions(fileBytes, options.marketplace);
  const output = await PDFDocument.create();
  const font = await output.embedFont(StandardFonts.Helvetica);
  const labels: PlacedLabel[] = [];
  const pages = source.getPages();

  for (let i = 0; i < pages.length; i++) {
    const page = pages[i];
    const { width, height } = page.getSize();
    const detectedRegions = detected?.[i];
    const alreadyFourUp = (detectedRegions?.length ?? 0) >= 3;
    const base: LabelRegion[] =
      detectedRegions ??
      (isThermalPage(width, height)
        ? [{ box: { left: 0, bottom: 0, right: width, top: height } }]
        : fallbackBoxes(width, height, options.marketplace).map((box) => ({ box })));
    for (const region of base) {
      const box = sourceBox(region, width, height, options.marketplace, alreadyFourUp);
      labels.push({
        embed: await embedRegion(output, page, { ...region, box }),
        sku: region.sku,
        rotate90: options.printer === "a4" && options.marketplace === "meesho" && !alreadyFourUp,
      });
    }
  }

  if (labels.length === 0) {
    throw new Error("No pages found in this PDF");
  }

  if (options.printer === "label") {
    for (const label of labels) {
      const page = output.addPage(THERMAL_4X6);
      placeThermal(page, label, font);
    }
    return output.save();
  }

  for (let i = 0; i < labels.length; i += 4) {
    const sheet = output.addPage(A4);
    labels.slice(i, i + 4).forEach((label, index) => {
      placeA4Cell(sheet, label, index, label.rotate90);
    });
  }

  return output.save();
}
