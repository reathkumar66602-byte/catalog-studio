export type MeeshoCalcInput = {
  sellingPrice: number;
  productCost: number;
  gstOnProduct: number;
  commissionPct: number;
  shipping: number;
  packing: number;
  returnRate: number;
  rtoReturnRate: number;
  ads: number;
  gstRegistered: boolean;
  dailyUnits: number;
};

export type MeeshoCalcResult = {
  commission: number;
  gst: number;
  returns: number;
  rtoReturn: number;
  ads: number;
  packing: number;
  productCost: number;
  shipping: number;
  profit: number;
  margin: number;
  monthlyUnits: number;
  monthlyRevenue: number;
  monthlyProfit: number;
  buyerPrice: number;
};

export const MEESHO_CATEGORIES = [
  { id: "sarees", label: "Sarees", shipping: 65, commission: 0 },
  { id: "women-ethnic", label: "Women ethnic wear", shipping: 60, commission: 0 },
  { id: "women-western", label: "Women western wear", shipping: 58, commission: 0 },
  { id: "men", label: "Men fashion", shipping: 62, commission: 0 },
  { id: "kids", label: "Kids wear", shipping: 55, commission: 0 },
  { id: "footwear", label: "Footwear", shipping: 85, commission: 0 },
  { id: "jewellery", label: "Jewellery & accessories", shipping: 45, commission: 0 },
  { id: "beauty", label: "Beauty & health", shipping: 50, commission: 0 },
  { id: "home", label: "Home & kitchen", shipping: 110, commission: 0 },
  { id: "electronics", label: "Electronics", shipping: 95, commission: 0 },
  { id: "bags", label: "Bags & luggage", shipping: 75, commission: 0 },
  { id: "other", label: "Other / unlisted", shipping: 70, commission: 0 },
] as const;

/**
 * Matches `price calculation meesho.xlsx` Sheet1:
 * GST = selling * GST%
 * Return = selling * return%
 * RTO = packing * rto%  (shown on the sheet; subtracted here because it was left out of Profit & Loss)
 * Profit = selling - commission - ads - GST - return - product cost - packing - RTO
 * Monthly units = 30 * daily
 * Buyer price = selling + shipping
 */
export function calculateMeeshoProfit(input: MeeshoCalcInput): MeeshoCalcResult {
  const sellingPrice = n(input.sellingPrice);
  const packing = n(input.packing);
  const commission = round2(sellingPrice * n(input.commissionPct) / 100);
  const gst = round2(sellingPrice * n(input.gstOnProduct) / 100);
  const returns = round2(sellingPrice * n(input.returnRate) / 100);
  const rtoReturn = round2(packing * n(input.rtoReturnRate) / 100);
  const ads = n(input.ads);
  const productCost = n(input.productCost);
  const shipping = n(input.shipping);
  let profit = round2(
    sellingPrice - commission - ads - gst - returns - productCost - packing - rtoReturn,
  );
  if (input.gstRegistered) {
    profit = round2(profit + gst);
  }
  const monthlyUnits = round2(30 * n(input.dailyUnits));
  const margin = sellingPrice > 0 ? round1((profit / sellingPrice) * 100) : 0;
  return {
    commission,
    gst,
    returns,
    rtoReturn,
    ads,
    packing,
    productCost,
    shipping,
    profit,
    margin,
    monthlyUnits,
    monthlyRevenue: round2(monthlyUnits * sellingPrice),
    monthlyProfit: round2(monthlyUnits * profit),
    buyerPrice: round2(sellingPrice + shipping),
  };
}

function n(value: number) {
  return Number.isFinite(value) ? value : 0;
}

function round2(value: number) {
  return Math.round(value * 100) / 100;
}

function round1(value: number) {
  return Math.round(value * 10) / 10;
}

export function inrExact(value: number) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: value % 1 === 0 ? 0 : 2,
  }).format(value);
}
