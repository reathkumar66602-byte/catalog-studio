import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { api } from "../api/client";
import { useAuth } from "../store/auth";
import type { SubscriptionStatus } from "../types";
import { useI18n } from "../i18n/LanguageProvider";
import { isStaff } from "./roles";

const ALLOWED_WHEN_LOCKED = ["/subscription", "/settings", "/billing-address", "/transactions"];

export function EntitlementGate() {
  const { user } = useAuth();
  const { t } = useI18n();
  const location = useLocation();
  const staff = isStaff(user?.role);
  const { data, isLoading } = useQuery({
    queryKey: ["sub"],
    queryFn: async () => (await api.get("/subscriptions/current")).data.data as SubscriptionStatus,
    enabled: Boolean(user) && !staff,
    staleTime: 30_000,
  });

  if (!user || staff) {
    return <Outlet />;
  }
  if (isLoading && !data) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 text-sm text-slate-500">
        {t("gate.checking")}
      </div>
    );
  }
  if (!data?.requiresRecharge) {
    return <Outlet />;
  }
  const allowed = ALLOWED_WHEN_LOCKED.some(
    (path) => location.pathname === path || location.pathname.startsWith(`${path}/`),
  );
  if (allowed) {
    return <Outlet />;
  }
  return <Navigate to="/subscription" replace />;
}
