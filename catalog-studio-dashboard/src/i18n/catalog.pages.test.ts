import { describe, expect, it } from "vitest";
import { LOCALES, translate } from "./catalog";
import { PAGES_CATALOG } from "./catalog.pages";
import { UI_CATALOG } from "./catalog.ui";

const LOCALE_CODES = LOCALES.map((item) => item.code);

describe("PAGES_CATALOG locale coverage", () => {
  it("has every key in all 10 locales", () => {
    const missing: string[] = [];
    for (const [key, row] of Object.entries(PAGES_CATALOG)) {
      for (const code of LOCALE_CODES) {
        if (!row[code]?.trim()) missing.push(`${key}.${code}`);
      }
    }
    expect(missing).toEqual([]);
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
      "nav.buyerBills",
      "nav.supplierStore",
      "nav.supplierInventory",
      "nav.supplierOrders",
      "nav.supplierBills",
    ] as const) {
      const row = UI_CATALOG[key];
      expect(row, key).toBeTruthy();
      for (const code of LOCALE_CODES) {
        expect(row[code]?.trim(), `${key}.${code}`).toBeTruthy();
      }
    }
  });
});
