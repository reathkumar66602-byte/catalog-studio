import type { PlanCard } from "../../types";

export type PlanGroup = "seller" | "supplier" | "pro_plus" | "tutorial";

export function planTitle(plan: { name: string; features?: Record<string, unknown> }) {
  const name = plan.features?.displayName;
  return typeof name === "string" && name.trim() ? name.trim() : plan.name;
}

export function planGroup(plan: { name: string; features?: Record<string, unknown> }): PlanGroup {
  const raw = String(plan.features?.group || "").toLowerCase();
  if (raw === "tutorial") return "tutorial";
  if (raw === "supplier") return "supplier";
  if (raw === "pro_plus" || raw === "business_term" || raw === "master") return "pro_plus";
  const code = plan.name.toUpperCase();
  if (code.startsWith("TUTORIAL_")) return "tutorial";
  if (code === "SUPPLIER" || code.startsWith("SUPPLIER_")) return "supplier";
  if (
    code === "PRO_PLUS" ||
    code === "MASTER" ||
    code.startsWith("MASTER_") ||
    code === "BUSINESS_HALF_YEARLY" ||
    code === "BUSINESS_YEARLY"
  ) {
    return "pro_plus";
  }
  return "seller";
}

export function termMonths(plan: Pick<PlanCard, "billingCycle" | "features">) {
  const stored = Number(plan.features?.termMonths);
  if (Number.isFinite(stored) && stored > 0) return stored;
  switch ((plan.billingCycle || "").toUpperCase()) {
    case "YEARLY":
    case "ANNUAL":
      return 12;
    case "HALF_YEARLY":
    case "HALFYEARLY":
    case "SEMIANNUAL":
      return 6;
    case "QUARTERLY":
      return 3;
    default:
      return 1;
  }
}

export function cycleUnit(cycle?: string) {
  switch ((cycle || "").toUpperCase()) {
    case "YEARLY":
    case "ANNUAL":
      return "year";
    case "HALF_YEARLY":
    case "HALFYEARLY":
    case "SEMIANNUAL":
      return "6 months";
    case "QUARTERLY":
      return "quarter";
    default:
      return "month";
  }
}

export function billedPhrase(cycle?: string) {
  switch ((cycle || "").toUpperCase()) {
    case "YEARLY":
    case "ANNUAL":
      return "billed yearly";
    case "HALF_YEARLY":
    case "HALFYEARLY":
    case "SEMIANNUAL":
      return "billed every 6 months";
    case "QUARTERLY":
      return "billed every quarter";
    default:
      return "billed monthly";
  }
}

export function isUnlimited(features?: Record<string, unknown>) {
  const flag = features?.unlimitedAccess;
  return flag === true || flag === "true";
}

export function includesStore(plan: { name: string; features?: Record<string, unknown> }) {
  const code = plan.name.toUpperCase();
  if (code === "SUPPLIER" || code.startsWith("SUPPLIER_")) return true;
  const flag = plan.features?.wholesaleSupplier;
  return flag === true || flag === "true";
}
