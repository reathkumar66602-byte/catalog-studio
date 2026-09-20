import { NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  Calculator,
  Chrome,
  CreditCard,
  Globe,
  History,
  LayoutDashboard,
  LogOut,
  Mail,
  MapPin,
  Menu,
  Scissors,
  Settings,
  Wallet,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useAuth } from "../store/auth";
import { api } from "../api/client";
import type { SubscriptionStatus } from "../types";
import { useI18n } from "../i18n/LanguageProvider";
import { LanguageSelect } from "../i18n/LanguageSelect";

export function AppLayout() {
  const { user, logout } = useAuth();
  const { t } = useI18n();
  const navigate = useNavigate();
  const [open, setOpen] = useState(true);
  const { data: access } = useQuery({
    queryKey: ["sub"],
    queryFn: async () => (await api.get("/subscriptions/current")).data.data as SubscriptionStatus,
    enabled: Boolean(user),
    staleTime: 30_000,
  });
  const links = [
    { to: "/dashboard", label: t("nav.dashboard"), icon: LayoutDashboard },
    { to: "/tools/labels", label: t("nav.labels"), icon: Scissors },
    { to: "/tools/meesho-calculator", label: t("nav.calculator"), icon: Calculator },
    { to: "/extension", label: t("nav.extension"), icon: Chrome },
    { to: "/subscription", label: t("nav.subscription"), icon: CreditCard },
    { to: "/analysis", label: t("nav.analysis"), icon: History },
    { to: "/billing-address", label: t("nav.billing"), icon: MapPin },
    { to: "/settings", label: t("nav.settings"), icon: Settings },
  ];
  const adminLinks = [
    { to: "/admin/site", label: t("nav.websiteSettings"), icon: Globe },
    { to: "/admin/billing", label: t("nav.trialPayment"), icon: Wallet },
    { to: "/settings/email-templates", label: t("nav.emailTemplates"), icon: Mail },
  ];
  const menu = user?.role === "ADMIN" ? [...links, ...adminLinks] : links;

  async function signOut() {
    try {
      await api.post("/auth/logout", { refreshToken: localStorage.getItem("cs_refresh") });
    } catch {
      // still clear local session
    }
    logout();
    navigate("/login");
  }

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <div className="flex min-h-screen">
        <aside
          className={`${open ? "w-64" : "w-20"} hidden shrink-0 border-r border-slate-200 bg-white transition-all dark:border-slate-800 dark:bg-slate-900 md:flex md:flex-col`}
        >
          <div className="flex items-center gap-3 px-5 py-5">
            <img src="/logo.svg" alt="Catalog Studio" className="h-10 w-10 rounded-xl" />
            {open && (
              <div>
                <p className="font-semibold">Catalog Studio</p>
                <p className="text-xs text-slate-500">{t("brand.workspace")}</p>
              </div>
            )}
          </div>
          <nav className="flex-1 space-y-1 px-3">
            {menu.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                className={({ isActive }) =>
                  `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium ${
                    isActive
                      ? "bg-teal-50 text-teal-800 dark:bg-teal-900/40 dark:text-teal-200"
                      : "text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
                  }`
                }
              >
                <link.icon size={18} />
                {open && <span>{link.label}</span>}
              </NavLink>
            ))}
          </nav>
          <button
            onClick={signOut}
            className="m-3 flex items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
          >
            <LogOut size={18} />
            {open && t("nav.logout")}
          </button>
        </aside>
        <div className="flex min-w-0 flex-1 flex-col">
          <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 dark:border-slate-800 dark:bg-slate-900">
            <button className="rounded-lg p-2 hover:bg-slate-100 dark:hover:bg-slate-800" onClick={() => setOpen(!open)}>
              <Menu size={20} />
            </button>
            <div className="flex items-center gap-3 sm:gap-4">
              <LanguageSelect compact />
              <NavLink to="/" className="hidden text-sm text-slate-500 hover:text-teal-700 sm:inline">
                {t("header.website")}
              </NavLink>
              <div className="text-right">
                <p className="text-sm font-medium">{user?.name}</p>
                <p className="text-xs text-slate-500">
                  {access?.trialActive
                    ? t("header.trialLeft", { days: access.daysRemaining })
                    : t("header.plan", { plan: user?.plan || access?.plan || "FREE" })}
                </p>
              </div>
            </div>
          </header>
          <main className="flex-1 p-4 md:p-8">
            {access?.trialActive && (
              <div className="mb-4 rounded-2xl border border-teal-200 bg-teal-50 px-4 py-3 text-sm text-teal-900">
                {access.daysRemaining <= 0
                  ? t("trial.bannerToday", { plan: access.plan })
                  : t("trial.bannerDays", { plan: access.plan, days: access.daysRemaining })}{" "}
                {t("trial.rechargeAfter")}
              </div>
            )}
            {access?.requiresRecharge && (
              <div className="mb-4 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
                {t("recharge.banner", {
                  headline: access.rechargeHeadline,
                  email: access.registeredEmail,
                })}
              </div>
            )}
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}
