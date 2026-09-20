export type ProfitInput = {
  sellingPrice: number;
  productCost: number;
  shipping: number;
  packaging: number;
  commissionPct: number;
  gstPct: number;
  promoType?: string;
  promoValue?: number;
  applyPromo?: boolean;
};

export type ProfitResult = {
  commission: number;
  gst: number;
  promoDiscount: number;
  netRevenue: number;
  totalCost: number;
  profit: number;
  marginPct: number;
};

export const MARKETPLACE_COMMISSION: Record<string, number> = {
  meesho: 18,
  flipkart: 15,
  amazon: 12,
};

export function calculateProfit(input: ProfitInput): ProfitResult {
  const sellingPrice = Math.max(0, Number(input.sellingPrice) || 0);
  const productCost = Math.max(0, Number(input.productCost) || 0);
  const shipping = Math.max(0, Number(input.shipping) || 0);
  const packaging = Math.max(0, Number(input.packaging) || 0);
  const commissionPct = Math.max(0, Number(input.commissionPct) || 0);
  const gstPct = Math.max(0, Number(input.gstPct) || 0);

  let promoDiscount = 0;
  if (input.applyPromo && (input.promoValue || 0) > 0) {
    if (input.promoType === "PERCENT") {
      promoDiscount = sellingPrice * (Number(input.promoValue) / 100);
    } else if (input.promoType === "FIXED") {
      promoDiscount = Number(input.promoValue);
    }
  }
  promoDiscount = Math.min(promoDiscount, sellingPrice);

  const netRevenue = sellingPrice - promoDiscount;
  const commission = netRevenue * (commissionPct / 100);
  const gst = netRevenue * (gstPct / 100);
  const totalCost = productCost + shipping + packaging + commission + gst;
  const profit = netRevenue - totalCost;
  const marginPct = netRevenue > 0 ? (profit / netRevenue) * 100 : 0;

  return {
    commission: round2(commission),
    gst: round2(gst),
    promoDiscount: round2(promoDiscount),
    netRevenue: round2(netRevenue),
    totalCost: round2(totalCost),
    profit: round2(profit),
    marginPct: round2(marginPct),
  };
}

function round2(value: number) {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}
