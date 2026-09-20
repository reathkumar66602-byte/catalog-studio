export type LabelMarketplace = "flipkart" | "meesho";

export type Box = { left: number; bottom: number; right: number; top: number };

export type LabelRegion = {
  box: Box;
  sku?: string;
};

export type TextItem = { str: string; x: number; y: number; fontSize: number };

const SKU_LABEL_RE = /^(sku|seller\s*sku|sku\s*id|style\s*id)\s*[:.\-]?$/i;
const SKU_INLINE_RE = /(?:sku|seller\s*sku|sku\s*id)\s*[:.\-]?\s*([A-Za-z0-9][A-Za-z0-9/_-]{1,40})/i;
const ANCHOR_RE = /awb|sku|order\s*(id|no)|sub[-\s]?order|tracking/i;
const INVOICE_HEADING_RE = /tax\s*invoice|bill\s*of\s*supply/i;

function quadrant(x: number, y: number, width: number, height: number) {
  const col = x < width / 2 ? 0 : 1;
  const row = y < height / 2 ? 1 : 0;
  return row * 2 + col;
}

function quadrants(width: number, height: number): Box[] {
  const midX = width / 2;
  const midY = height / 2;
  return [
    { left: 0, bottom: midY, right: midX, top: height },
    { left: midX, bottom: midY, right: width, top: height },
    { left: 0, bottom: 0, right: midX, top: midY },
    { left: midX, bottom: 0, right: width, top: midY },
  ];
}

function skuFromItems(items: TextItem[]) {
  const fmpp = items.find((item) => /^FMPP[A-Z0-9]+$/i.test(item.str.trim()));
  if (fmpp) return fmpp.str.trim();

  const ordered = [...items].sort((a, b) => b.y - a.y || a.x - b.x);
  for (let i = 0; i < ordered.length; i++) {
    const inline = ordered[i].str.match(SKU_INLINE_RE);
    if (inline?.[1] && !/^(id|description)$/i.test(inline[1])) return inline[1].trim();
    if (SKU_LABEL_RE.test(ordered[i].str.trim()) || ordered[i].str.trim() === "SKU") {
      const label = ordered[i];
      const below = items
        .filter(
          (item) =>
            Math.abs(item.x - label.x) < 36 &&
            item.y < label.y - 2 &&
            item.y > label.y - 48 &&
            /[A-Za-z0-9]/.test(item.str) &&
            !/^(size|qty|color|order|description)$/i.test(item.str.trim()),
        )
        .sort((a, b) => b.y - a.y);
      if (below[0]) return below[0].str.trim().slice(0, 40);
      const next = ordered[i + 1]?.str.replace(/^[:.\-\s]+/, "").trim();
      if (next && /[A-Za-z0-9]/.test(next) && !/^(size|qty|color|id)$/i.test(next)) {
        return next.slice(0, 40);
      }
    }
  }
  return undefined;
}

function fallbackRatio(marketplace: LabelMarketplace) {
  // Flipkart: top shipping block. Meesho: through invoice line items (above Total).
  return marketplace === "flipkart" ? 0.46 : 0.64;
}

export function fallbackBoxes(width: number, height: number, marketplace: LabelMarketplace): Box[] {
  const cropHeight = height * fallbackRatio(marketplace);
  return [
    {
      left: 0,
      bottom: Math.max(0, height - cropHeight),
      right: width,
      top: height,
    },
  ];
}

/** Quick Label Crop Flipkart window: 265×350, centered, 22pt from the page top. */
export function flipkartSlipBox(width: number, height: number): Box {
  if (Math.abs(width - 595.28) < 3 && Math.abs(height - 841.89) < 3) {
    return { left: 165, bottom: 470, right: 430, top: 820 };
  }
  const slipW = 265;
  const slipH = 350;
  const topMargin = 22;
  const left = (width - slipW) / 2;
  return {
    left,
    bottom: height - topMargin - slipH,
    right: left + slipW,
    top: height - topMargin,
  };
}

function meeshoTotalCut(items: TextItem[], width: number, height: number): Box | null {
  const invoice = items.find((item) => INVOICE_HEADING_RE.test(item.str));
  const invoiceY = invoice?.y ?? height * 0.6;
  const totals = items.filter(
    (item) => /^total$/i.test(item.str.trim()) && item.y > 40 && item.y < invoiceY - 16,
  );
  if (totals.length > 0) {
    const rowTotals = totals.filter((item) => item.x < width * 0.3);
    const chosen = (rowTotals.length ? rowTotals : totals).reduce((best, item) =>
      item.y < best.y ? item : best,
    );
    if (height - chosen.y >= 150) {
      return { left: 0, bottom: chosen.y, right: width, top: height };
    }
  }
  const footer = items.find((item) => /tax is not payable|computer generated invoice/i.test(item.str));
  if (footer && height - footer.y >= 150) {
    return { left: 0, bottom: footer.y + (footer.fontSize || 10) + 6, right: width, top: height };
  }
  return null;
}

function invoiceCut(items: TextItem[], width: number, height: number, marketplace: LabelMarketplace): Box | null {
  if (marketplace === "meesho") {
    const totalCut = meeshoTotalCut(items, width, height);
    if (totalCut) return totalCut;
  }
  const invoices = items.filter((item) => INVOICE_HEADING_RE.test(item.str));
  if (invoices.length === 0) return null;
  const heading = invoices.reduce((best, item) => (item.y > best.y ? item : best));
  if (heading.y > height * 0.72) return null;
  const bottom =
    marketplace === "meesho" ? heading.y - 8 : heading.y + heading.fontSize + 6;
  if (bottom < 20 || height - bottom < 150) return null;
  return { left: 0, bottom, right: width, top: height };
}

export function regionsFromText(
  items: TextItem[],
  width: number,
  height: number,
  marketplace: LabelMarketplace,
): LabelRegion[] {
  const hits = [0, 0, 0, 0];
  for (const item of items) {
    if (ANCHOR_RE.test(item.str)) hits[quadrant(item.x, item.y, width, height)] += 1;
  }
  const filled = hits.filter((count) => count > 0).length;
  const invoiceCount = items.filter((item) => INVOICE_HEADING_RE.test(item.str)).length;

  if (filled >= 3 && invoiceCount === 0) {
    return quadrants(width, height).map((box, index) => ({
      box,
      sku: skuFromItems(items.filter((item) => quadrant(item.x, item.y, width, height) === index)),
    }));
  }

  const cut = invoiceCut(items, width, height, marketplace);
  if (cut) {
    return [{ box: cut, sku: skuFromItems(items.filter((item) => item.y >= cut.bottom)) }];
  }

  return fallbackBoxes(width, height, marketplace).map((box) => ({
    box,
    sku: skuFromItems(items.filter((item) => item.y >= box.bottom)),
  }));
}
