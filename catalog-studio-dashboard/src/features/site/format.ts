export function formatMoney(value: number) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(value);
}

export function promoLabel(type: string, value: number, trialDays = 0) {
  if (type === "TRIAL") return `${trialDays || value} day trial`;
  if (type === "FIXED") return formatMoney(value) + " off";
  return `${value}% off`;
}
