import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ShieldAlert, Store } from "lucide-react";
import { useMemo } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../../api/client";
import type { PlanCard, SubscriptionStatus } from "../../types";
import { useI18n } from "../../i18n/LanguageProvider";
import { canOpenSupplierStore, canSupply } from "../wholesale/wholesale";
import { useAuth } from "../../store/auth";

function quota(value: unknown) {
  const count = Number(value);
  return Number.isFinite(count) ? count.toLocaleString("en-IN") : "—";
}

export function SubscriptionPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { user } = useAuth();

  const { data: current } = useQuery({
    queryKey: ["sub"],
    queryFn: async () => (await api.get("/subscriptions/current")).data.data as SubscriptionStatus,
  });
  const { data: plans } = useQuery({
    queryKey: ["plans"],
    queryFn: async () => (await api.get("/subscriptions/plans")).data.data as PlanCard[],
  });

  const locked = Boolean(current?.requiresRecharge);

  const statusLabel = useMemo(() => {
    if (!current) return t("sub.loading");
    if (current.trialActive) {
      return current.daysRemaining <= 0
        ? t("sub.trialToday")
        : t("sub.trialDays", { days: current.daysRemaining });
    }
    if (current.accessEntitled) return t("sub.planActive", { plan: current.plan });
    if (current.effectiveStatus === "PAYMENT_PENDING") return t("sub.pending");
    return t("sub.ended");
  }, [current, t]);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{t("app.subTitle")}</h1>
          <p className="text-slate-500">{statusLabel}</p>
        </div>
        <Link to="/transactions" className="text-sm font-medium text-teal-700 hover:underline">
          View transactions & bills
        </Link>
      </div>

      {current?.trialActive && (
        <div className="rounded-2xl border border-teal-200 bg-teal-50 px-5 py-4 text-sm text-teal-900">
          {t("sub.trialBanner", {
            days: current.trialDaysConfigured,
            plan: current.plan,
            end: current.endDate || t("sub.ended"),
          })}
        </div>
      )}

      {locked && (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4 text-amber-950">
          <p className="flex items-center gap-2 font-semibold">
            <ShieldAlert size={18} />
            {current?.rechargeHeadline || t("sub.recharge")}
          </p>
          <p className="mt-2 text-sm leading-relaxed">{current?.rechargeBody}</p>
        </div>
      )}

      {current?.pendingPlan && current.effectiveStatus === "PAYMENT_PENDING" && (
        <div className="rounded-2xl border border-slate-200 bg-white px-5 py-4 text-sm text-slate-700">
          Payment pending for <span className="font-semibold">{current.pendingPlan}</span>.{" "}
          <button
            type="button"
            className="font-medium text-teal-700 hover:underline"
            onClick={() => navigate(`/subscription/pay?plan=${encodeURIComponent(current.pendingPlan || "")}`)}
          >
            Continue to payment
          </button>
        </div>
      )}

      {canOpenSupplierStore(current, user?.role) && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-teal-200 bg-teal-50 px-5 py-4 text-sm text-teal-950">
          <div className="flex items-start gap-2">
            <Store size={18} className="mt-0.5 shrink-0" />
            <div>
              <p className="font-semibold">
                {canSupply(current) ? "Wholesale supplier access is active" : "Staff can open a wholesale store"}
              </p>
              <p className="mt-1 text-teal-900/80">
                Open your store next. We verify your GSTIN with gstinapi.in and lock the store name to the GST trade name.
              </p>
            </div>
          </div>
          <Link to="/supplier/store" className="rounded-xl bg-teal-700 px-4 py-2.5 font-semibold text-white hover:bg-teal-800">
            Create / manage store
          </Link>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {(plans || [])
          .filter((plan) => plan.purchasable)
          .map((plan) => {
            const active = current?.plan === plan.name && current.accessEntitled;
            const supplierPlan =
              plan.name?.toUpperCase() === "SUPPLIER" ||
              plan.features?.wholesaleSupplier === true ||
              plan.features?.wholesaleSupplier === "true";
            return (
              <article
                key={plan.name}
                className={`flex flex-col rounded-2xl border p-5 ${
                  active
                    ? "border-teal-700 bg-teal-50"
                    : supplierPlan
                      ? "border-teal-300 bg-white shadow-sm shadow-teal-100"
                      : "border-slate-200 bg-white"
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <h3 className="font-semibold">{plan.name}</h3>
                  {supplierPlan && (
                    <span className="rounded-full bg-teal-100 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-teal-800">
                      Wholesale store
                    </span>
                  )}
                </div>
                <p className="mt-2 text-3xl font-semibold">
                  ₹{Number(plan.price).toLocaleString("en-IN")}
                  <span className="ml-1 text-sm font-normal text-slate-500">
                    / {plan.billingCycle?.toLowerCase()}
                  </span>
                </p>
                <p className="mt-1 text-xs text-slate-500">Taxes & promo applied on the next step</p>
                <ul className="mt-4 flex-1 space-y-1 text-sm text-slate-600">
                  <li>{t("sub.aiMonth", { n: quota(plan.features?.monthlyAiAnalyses) })}</li>
                  <li>{t("sub.shootMonth", { n: quota(plan.features?.monthlyShootPhotos) })}</li>
                  <li>{t("sub.trendingMonth", { n: quota(plan.features?.monthlyTrendingProducts) })}</li>
                  <li>{t("sub.labelUnlimited")}</li>
                  <li>{t("sub.ecommerceTips")}</li>
                  {supplierPlan && <li className="font-medium text-teal-800">Open a GST-verified wholesale store</li>}
                </ul>
                <button
                  type="button"
                  onClick={() => {
                    void qc.invalidateQueries({ queryKey: ["sub"] });
                    navigate(`/subscription/pay?plan=${encodeURIComponent(plan.name)}`);
                  }}
                  className="mt-5 rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800"
                >
                  {active ? t("sub.extend") : t("sub.rechargePlan")}
                </button>
              </article>
            );
          })}
      </div>
    </div>
  );
}
