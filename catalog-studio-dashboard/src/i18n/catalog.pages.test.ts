import { describe, expect, it } from "vitest";
import { LOCALES, translate } from "./catalog";
import { PAGES_CATALOG } from "./catalog.pages";
import { UI_CATALOG } from "./catalog.ui";

/** Locales that require full catalog rows. gu/or are selectable and fall back to en. */
const FULL_LOCALE_CODES = LOCALES.map((item) => item.code).filter(
  (code) => code !== "gu" && code !== "or",
);

describe("PAGES_CATALOG locale coverage", () => {
  it("has every key in all fully-translated locales", () => {
    const missing: string[] = [];
    for (const [key, row] of Object.entries(PAGES_CATALOG)) {
      for (const code of FULL_LOCALE_CODES) {
        if (!row[code]?.trim()) missing.push(`${key}.${code}`);
      }
    }
    expect(missing).toEqual([]);
  });

  it("includes Gujarati and Odia in LOCALES for selection", () => {
    expect(LOCALES.some((item) => item.code === "gu")).toBe(true);
    expect(LOCALES.some((item) => item.code === "or")).toBe(true);
  });

  it("translates store GSTIN hint and Verify GST in Hindi", () => {
    expect(translate("hi", "store.verifyGst")).toBe("GST सत्यापित करें");
    expect(translate("hi", "store.titleCreate")).toContain("होलसेल");
    expect(translate("hi", "store.gstinHintShort")).toMatch(/15/);
    expect(translate("hi", "store.gstinHintCount", { count: 14 })).toContain("14");
  });

  it("translates staff nav and role labels", () => {
    expect(translate("hi", "nav.orderManager")).toBe("ऑर्डर मैनेजर");
    expect(translate("hi", "nav.marketplaces")).toContain("OMS");
    expect(translate("hi", "role.superAdmin")).toBe("सुपर एडमिन");
    expect(translate("ja", "role.superAdmin")).toBe("スーパー管理者");
  });
});

describe("wholesale nav UI_CATALOG", () => {
  it("has full locale rows for supplier nav keys", () => {
    for (const key of [
      "nav.liveStock",
      "nav.wholesaleOrders",
      "nav.goodsReceipt",
      "nav.buyerBills",
      "nav.supplierStore",
      "nav.supplierInventory",
      "nav.supplierOrders",
      "nav.supplierBills",
    ] as const) {
      const row = UI_CATALOG[key];
      expect(row, key).toBeTruthy();
      for (const code of FULL_LOCALE_CODES) {
        expect(row[code]?.trim(), `${key}.${code}`).toBeTruthy();
      }
    }
  });
});
