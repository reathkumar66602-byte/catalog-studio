import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
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
  X,
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
  const location = useLocation();
  const [desktopOpen, setDesktopOpen] = useState(true);
  const [mobileOpen, setMobileOpen] = useState(false);
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
    setMobileOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!mobileOpen) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, [mobileOpen]);

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

  function toggleMenu() {
    if (typeof window !== "undefined" && window.matchMedia("(min-width: 768px)").matches) {
      setDesktopOpen((value) => !value);
      return;
    }
    setMobileOpen((value) => !value);
  }

  return (
    <div className="min-h-dvh bg-slate-50 text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <div className="flex min-h-dvh">
        <aside
          className={`${desktopOpen ? "w-64" : "w-20"} hidden min-h-0 shrink-0 border-r border-slate-200 bg-white transition-all dark:border-slate-800 dark:bg-slate-900 md:flex md:flex-col`}
        >
          <div className="flex items-center gap-3 px-5 py-5">
            <img src="/logo.svg" alt="Catalog Studio" className="h-10 w-10 rounded-xl" />
            {desktopOpen && (
              <div>
                <p className="font-semibold">Catalog Studio</p>
                <p className="text-xs text-slate-500">{t("brand.workspace")}</p>
              </div>
            )}
          </div>
          <SidebarNav menu={menu} open={desktopOpen} role={role} />
          <button
            onClick={signOut}
            className="m-3 flex items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
          >
            <LogOut size={18} />
            {desktopOpen && t("nav.logout")}
          </button>
        </aside>

        {mobileOpen && (
          <div className="fixed inset-0 z-50 md:hidden" role="dialog" aria-modal="true" aria-label="Navigation menu">
            <button
              type="button"
              className="absolute inset-0 bg-slate-950/45"
              aria-label="Close menu"
              onClick={() => setMobileOpen(false)}
            />
            <aside className="absolute inset-y-0 left-0 flex w-[min(18rem,88vw)] flex-col bg-white shadow-2xl dark:bg-slate-900">
              <div className="flex shrink-0 items-center justify-between gap-3 border-b border-slate-200 px-4 py-4 dark:border-slate-800">
                <div className="flex min-w-0 items-center gap-3">
                  <img src="/logo.svg" alt="" className="h-9 w-9 shrink-0 rounded-xl" />
                  <div className="min-w-0">
                    <p className="truncate font-semibold">Catalog Studio</p>
                    <p className="truncate text-xs text-slate-500">{t("brand.workspace")}</p>
                  </div>
                </div>
                <button
                  type="button"
                  className="rounded-lg p-2 hover:bg-slate-100 dark:hover:bg-slate-800"
                  aria-label="Close menu"
                  onClick={() => setMobileOpen(false)}
                >
                  <X size={20} />
                </button>
              </div>
              <SidebarNav menu={menu} open role={role} />
              <button
                onClick={signOut}
                className="m-3 flex shrink-0 items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800"
              >
                <LogOut size={18} />
                {t("nav.logout")}
              </button>
            </aside>
          </div>
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="sticky top-0 z-20 flex items-center justify-between gap-2 border-b border-slate-200 bg-white px-3 py-3 pt-[max(0.75rem,env(safe-area-inset-top))] dark:border-slate-800 dark:bg-slate-900 sm:px-4">
            <button
              type="button"
              className="shrink-0 rounded-lg p-2 hover:bg-slate-100 dark:hover:bg-slate-800"
              aria-label={mobileOpen ? "Close menu" : "Open menu"}
              aria-expanded={mobileOpen}
              onClick={toggleMenu}
            >
              <Menu size={20} />
            </button>
            <div className="flex min-w-0 items-center gap-2 sm:gap-4">
              <LanguageSelect compact />
              <NavLink to="/" className="hidden text-sm text-slate-500 hover:text-teal-700 sm:inline">
                {t("header.website")}
              </NavLink>
              <div className="min-w-0 text-right">
                <p className="truncate text-sm font-medium max-w-[7.5rem] sm:max-w-[14rem]">{user?.name}</p>
                <p className="truncate text-xs text-slate-500 max-w-[7.5rem] sm:max-w-[14rem]">
                  {isStaff(role)
                    ? roleLabel(role)
                    : access?.trialActive
                      ? t("header.trialLeft", { days: access.daysRemaining })
                      : t("header.plan", { plan: user?.plan || access?.plan || "FREE" })}
                </p>
              </div>
            </div>
          </header>
          <main className="flex-1 overflow-x-auto p-4 pb-[max(1rem,env(safe-area-inset-bottom))] md:p-8">
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

function SidebarNav({
  menu,
  open,
  role,
}: {
  menu: { workspace: MenuLink[]; staff: MenuLink[] };
  open: boolean;
  role?: string;
}) {
  return (
    <nav className="min-h-0 flex-1 space-y-1 overflow-y-auto overscroll-contain px-3 py-2 pb-[max(0.5rem,env(safe-area-inset-bottom))]">
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
      <link.icon size={18} className="shrink-0" />
      {open && <span className="truncate">{link.label}</span>}
    </NavLink>
  );
}
