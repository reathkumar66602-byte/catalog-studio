import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { api } from "../api/client";
import { Calculator, Camera, Chrome, History, MapPin, Scissors, ShieldAlert, Sparkles, TrendingUp } from "lucide-react";
import { useI18n } from "../i18n/LanguageProvider";
import { useAuth } from "../store/auth";
import type { SubscriptionStatus } from "../types";

export function DashboardPage() {
  const { t } = useI18n();
  const { user } = useAuth();
  const { data, isLoading } = useQuery({
    queryKey: ["dashboard"],
    queryFn: async () => (await api.get("/dashboard")).data.data,
  });
  const { data: sub } = useQuery({
    queryKey: ["sub"],
    queryFn: async () => (await api.get("/subscriptions/current")).data.data as SubscriptionStatus,
    enabled: user != null && user.role !== "ADMIN" && user.role !== "SUPERADMIN",
  });

  const needsRecharge = Boolean(data?.requiresRecharge || sub?.requiresRecharge || user?.requiresRecharge);

  const cards = [
    { label: t("dash.aiThisMonth"), value: data?.aiAnalysesThisMonth ?? 0 },
    { label: t("dash.currentPlan"), value: data?.currentPlan ?? "FREE" },
  ];
  const actions = [
    { to: "/trending", icon: TrendingUp, label: t("nav.trending") },
    { to: "/shoot", icon: Camera, label: t("nav.shoot") },
    { to: "/products/analyze", icon: Sparkles, label: t("nav.productAnalysis") },
    { to: "/tools/labels", icon: Scissors, label: t("nav.labels") },
    { to: "/tools/meesho-calculator", icon: Calculator, label: t("nav.calculator") },
    { to: "/extension", icon: Chrome, label: t("dash.extension") },
    { to: "/analysis", icon: History, label: t("nav.analysis") },
    { to: "/billing-address", icon: MapPin, label: t("nav.billing") },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold">{t("dash.title")}</h1>
        <p className="text-slate-500">{t("dash.subtitle")}</p>
      </div>

      {needsRecharge && (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4 text-amber-950">
          <p className="flex items-center gap-2 font-semibold">
            <ShieldAlert size={18} />
            {sub?.rechargeHeadline || t("dash.rechargeTitle")}
          </p>
          <p className="mt-2 text-sm leading-relaxed">
            {sub?.rechargeBody || t("dash.rechargeHint")}
          </p>
          <Link
            to="/subscription"
            className="mt-4 inline-flex rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-800"
          >
            {t("dash.rechargeCta")}
          </Link>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {cards.map((card) => (
          <div key={card.label} className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-800 dark:bg-slate-900">
            <p className="text-sm text-slate-500">{card.label}</p>
            <p className="mt-2 text-2xl font-semibold">{isLoading ? "—" : card.value}</p>
          </div>
        ))}
      </div>
      <div>
        <h2 className="mb-3 font-medium">{t("dash.quickActions")}</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {actions.map((item) => (
            <Action key={item.to} to={item.to} icon={item.icon} label={item.label} />
          ))}
        </div>
      </div>
    </div>
  );
}

function Action({ to, icon: Icon, label }: { to: string; icon: typeof Scissors; label: string }) {
  return (
    <Link
      to={to}
      className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white px-4 py-4 font-medium hover:border-teal-600 dark:border-slate-800 dark:bg-slate-900"
    >
      <Icon size={18} className="text-teal-700" />
      {label}
    </Link>
  );
}
