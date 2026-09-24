import { NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  Calculator,
  Camera,
  Chrome,
  CreditCard,
  Globe,
  History,
  KeyRound,
  LayoutDashboard,
  LogOut,
  TrendingUp,
  Mail,
  MapPin,
  Menu,
  Receipt,
  Scissors,
  Settings,
  Shield,
  Users,
  Wallet,
} from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useAuth } from "../store/auth";
import { api } from "../api/client";
import type { AccountProfile, SubscriptionStatus, UserSummary } from "../types";
import { useI18n } from "../i18n/LanguageProvider";
import { LanguageSelect } from "../i18n/LanguageSelect";
import { hasFeature, isStaff, isSuperAdmin, roleLabel } from "../routes/roles";

type MenuLink = {
  to: string;
  label: string;
  icon: typeof LayoutDashboard;
  feature?: string;
  superAdminOnly?: boolean;
};

export function AppLayout() {
  const { user, updateUser, logout } = useAuth();
  const { t } = useI18n();
  const navigate = useNavigate();
  const [open, setOpen] = useState(true);
  const { data: access } = useQuery({
    queryKey: ["sub"],
    queryFn: async () => (await api.get("/subscriptions/current")).data.data as SubscriptionStatus,
    enabled: Boolean(user),
    staleTime: 30_000,
  });
  const { data: me } = useQuery({
    queryKey: ["me"],
    queryFn: async () => (await api.get("/me")).data.data as AccountProfile,
    enabled: Boolean(user),
    staleTime: 0,
  });

  useEffect(() => {
    if (!me || !user) return;
    const next: UserSummary = {
      ...user,
      name: me.name || user.name,
      email: me.email || user.email,
      role: me.role || user.role,
      plan: me.plan ?? user.plan,
      planStatus: me.planStatus ?? user.planStatus,
      accessEntitled: me.accessEntitled ?? user.accessEntitled,
      requiresRecharge: me.requiresRecharge ?? user.requiresRecharge,
      enabledFeatures: me.enabledFeatures ?? user.enabledFeatures,
    };
    if (
      next.role === user.role &&
      next.name === user.name &&
      JSON.stringify(next.enabledFeatures || []) === JSON.stringify(user.enabledFeatures || [])
    ) {
      return;
    }
    updateUser(next);
  }, [me, user, updateUser]);

  const role = me?.role || user?.role;
  const enabledFeatures = me?.enabledFeatures || user?.enabledFeatures;
  const links: MenuLink[] = [
    { to: "/dashboard", label: t("nav.dashboard"), icon: LayoutDashboard, feature: "dashboard" },
    { to: "/trending", label: t("nav.trending"), icon: TrendingUp, feature: "trending" },
    { to: "/shoot", label: t("nav.shoot"), icon: Camera, feature: "shoot" },
    { to: "/tools/labels", label: t("nav.labels"), icon: Scissors, feature: "labels" },
    { to: "/tools/meesho-calculator", label: t("nav.calculator"), icon: Calculator, feature: "meesho_calculator" },
    { to: "/extension", label: t("nav.extension"), icon: Chrome, feature: "extension" },
    { to: "/subscription", label: t("nav.subscription"), icon: CreditCard, feature: "subscription" },
    { to: "/transactions", label: t("nav.transactions"), icon: Receipt, feature: "transactions" },
    { to: "/analysis", label: t("nav.analysis"), icon: History, feature: "analysis" },
    { to: "/billing-address", label: t("nav.billing"), icon: MapPin, feature: "billing_address" },
    { to: "/settings", label: t("nav.settings"), icon: Settings, feature: "settings" },
  ];
  const staffLinks: MenuLink[] = [
    { to: "/admin/users", label: t("nav.users"), icon: Users },
    { to: "/admin/access", label: t("nav.access"), icon: KeyRound },
    { to: "/admin/staff", label: t("nav.admin"), icon: Shield, superAdminOnly: true },
    { to: "/admin/site", label: t("nav.websiteSettings"), icon: Globe },
    { to: "/admin/billing", label: t("nav.trialPayment"), icon: Wallet },
    { to: "/settings/email-templates", label: t("nav.emailTemplates"), icon: Mail },
  ];
  const menu = useMemo(() => {
    const workspace = links.filter(
      (link) => !link.feature || hasFeature({ role, enabledFeatures }, link.feature),
    );
    const staff = isStaff(role)
      ? staffLinks.filter((link) => !link.superAdminOnly || isSuperAdmin(role))
      : [];
    return { workspace, staff };
  }, [role, enabledFeatures, t]);

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
          <nav className="flex-1 space-y-1 overflow-y-auto px-3">
            {menu.staff.length > 0 && (
              <>
                {open && (
                  <p className="px-3 pt-1 text-[11px] font-semibold uppercase tracking-wide text-teal-700">
                    {roleLabel(role)}
                  </p>
                )}
                {menu.staff.map((link) => (
                  <SideLink key={link.to} link={link} open={open} />
                ))}
                <div className="my-2 border-t border-slate-200 dark:border-slate-800" />
              </>
            )}
            {menu.workspace.map((link) => (
              <SideLink key={link.to} link={link} open={open} />
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
                  {isStaff(role)
                    ? roleLabel(role)
                    : access?.trialActive
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

function SideLink({ link, open }: { link: MenuLink; open: boolean }) {
  return (
    <NavLink
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
  );
}
