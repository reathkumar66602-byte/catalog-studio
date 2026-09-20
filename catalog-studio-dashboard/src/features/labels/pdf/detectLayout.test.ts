import { describe, expect, it } from "vitest";
import { fallbackBoxes, flipkartSlipBox, regionsFromText } from "./layoutMath";

const A4_W = 595.28;
const A4_H = 841.89;

describe("regionsFromText", () => {
  it("cuts the page above TAX INVOICE", () => {
    const regions = regionsFromText(
      [
        { str: "Meesho shipping label", x: 40, y: 760, fontSize: 14 },
        { str: "SKU ABC123", x: 40, y: 500, fontSize: 10 },
        { str: "TAX INVOICE", x: 40, y: 380, fontSize: 14 },
        { str: "GST 18%", x: 40, y: 80, fontSize: 10 },
      ],
      A4_W,
      A4_H,
      "meesho",
    );
    expect(regions).toHaveLength(1);
    expect(regions[0].box.bottom).toBeGreaterThan(350);
    expect(regions[0].box.bottom).toBeLessThan(380);
    expect(regions[0].box.top).toBe(A4_H);
    expect(regions[0].sku).toBe("ABC123");
  });

  it("uses Quick Label Crop's 265x350 Flipkart slip window", () => {
    expect(flipkartSlipBox(A4_W, A4_H)).toEqual({ left: 165, bottom: 470, right: 430, top: 820 });
  });

  it("drops Flipkart Tax Invoice but keeps the FMPP SKU", () => {
    const regions = regionsFromText(
      [
        { str: "E-Kart Logistics", x: 216, y: 802, fontSize: 6 },
        { str: "FMPP4166301554", x: 199, y: 500, fontSize: 7 },
        { str: "Tax Invoice", x: 52, y: 442, fontSize: 8 },
        { str: "Invoice No:", x: 228, y: 443, fontSize: 6 },
      ],
      A4_W,
      A4_H,
      "flipkart",
    );
    expect(regions).toHaveLength(1);
    expect(regions[0].box.bottom).toBeGreaterThan(442);
    expect(regions[0].sku).toBe("FMPP4166301554");
  });

  it("reads Meesho SKU from the cell under the SKU heading", () => {
    const regions = regionsFromText(
      [
        { str: "Customer Address", x: 19, y: 812, fontSize: 10 },
        { str: "SKU", x: 19, y: 517, fontSize: 11 },
        { str: "Size", x: 203, y: 517, fontSize: 11 },
        { str: "29yQcC8u", x: 19, y: 501, fontSize: 11 },
        { str: "TAX INVOICE", x: 266, y: 482, fontSize: 10 },
        { str: "Total", x: 520, y: 430, fontSize: 9 },
        { str: "Total", x: 19, y: 293, fontSize: 10 },
      ],
      A4_W,
      A4_H,
      "meesho",
    );
    expect(regions[0].sku).toBe("29yQcC8u");
    expect(regions[0].box.bottom).toBe(293);
  });

  it("splits a 4-up Meesho sheet without an invoice", () => {
    const regions = regionsFromText(
      [
        { str: "SKU A1", x: 80, y: 700, fontSize: 10 },
        { str: "AWB 1", x: 80, y: 680, fontSize: 10 },
        { str: "SKU B2", x: 400, y: 700, fontSize: 10 },
        { str: "AWB 2", x: 400, y: 680, fontSize: 10 },
        { str: "SKU C3", x: 80, y: 200, fontSize: 10 },
        { str: "Order ID 3", x: 80, y: 180, fontSize: 10 },
        { str: "SKU D4", x: 400, y: 200, fontSize: 10 },
        { str: "AWB 4", x: 400, y: 180, fontSize: 10 },
      ],
      A4_W,
      A4_H,
      "meesho",
    );
    expect(regions).toHaveLength(4);
    expect(regions.map((region) => region.sku)).toEqual(["A1", "B2", "C3", "D4"]);
  });

  it("keeps a taller Meesho fallback than Flipkart so invoice rows stay", () => {
    const flipkart = fallbackBoxes(A4_W, A4_H, "flipkart")[0];
    const meesho = fallbackBoxes(A4_W, A4_H, "meesho")[0];
    expect(meesho.top - meesho.bottom).toBeGreaterThan(flipkart.top - flipkart.bottom);
  });
});
