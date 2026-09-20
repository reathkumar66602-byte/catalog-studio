import { describe, expect, it } from "vitest";
import { calculateProfit } from "./calculator";

describe("calculateProfit", () => {
  it("computes Meesho-style margin without a promo", () => {
    const result = calculateProfit({
      sellingPrice: 500,
      productCost: 220,
      shipping: 40,
      packaging: 10,
      commissionPct: 18,
      gstPct: 5,
    });
    expect(result.commission).toBe(90);
    expect(result.gst).toBe(25);
    expect(result.profit).toBe(115);
    expect(result.marginPct).toBe(23);
  });

  it("applies a Krishna Store percent promo as a customer discount", () => {
    const result = calculateProfit({
      sellingPrice: 500,
      productCost: 220,
      shipping: 40,
      packaging: 10,
      commissionPct: 18,
      gstPct: 5,
      applyPromo: true,
      promoType: "PERCENT",
      promoValue: 10,
    });
    expect(result.promoDiscount).toBe(50);
    expect(result.netRevenue).toBe(450);
    expect(result.profit).toBe(76.5);
  });
});
