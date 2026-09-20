import { describe, expect, it } from "vitest";
import { calculateMeeshoProfit } from "./meeshoMath";

describe("calculateMeeshoProfit", () => {
  it("matches price calculation meesho.xlsx sample numbers", () => {
    const result = calculateMeeshoProfit({
      sellingPrice: 280,
      productCost: 200,
      gstOnProduct: 5,
      commissionPct: 0,
      shipping: 65,
      packing: 5,
      returnRate: 20,
      rtoReturnRate: 10,
      ads: 0,
      gstRegistered: false,
      dailyUnits: 100,
    });
    expect(result.commission).toBe(0);
    expect(result.gst).toBe(14);
    expect(result.returns).toBe(56);
    expect(result.rtoReturn).toBe(0.5);
    expect(result.packing).toBe(5);
    expect(result.buyerPrice).toBe(345);
    expect(result.monthlyUnits).toBe(3000);
    expect(result.monthlyRevenue).toBe(840000);
    // Excel Profit & Loss skipped RTO (5). We subtract the RTO line (0.5).
    expect(result.profit).toBe(4.5);
    expect(result.monthlyProfit).toBe(13500);
  });

  it("matches Excel Profit & Loss when RTO is 0", () => {
    const result = calculateMeeshoProfit({
      sellingPrice: 280,
      productCost: 200,
      gstOnProduct: 5,
      commissionPct: 0,
      shipping: 65,
      packing: 5,
      returnRate: 20,
      rtoReturnRate: 0,
      ads: 0,
      gstRegistered: false,
      dailyUnits: 100,
    });
    expect(result.profit).toBe(5);
    expect(result.monthlyProfit).toBe(15000);
    expect(result.monthlyRevenue).toBe(840000);
    expect(result.buyerPrice).toBe(345);
  });
});
