import { Outlet } from "react-router-dom";

/**
 * Feature flags no longer hide routes — sellers see every tool in the menu.
 * EntitlementGate + API quotas still enforce recharge / plan limits.
 */
export function FeatureRoute({ feature: _feature }: { feature: string }) {
  return <Outlet />;
}
