import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ShieldAlert, Store } from "lucide-react";
import { useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../../api/client";
import type { PlanCard, SubscriptionStatus } from "../../types";
import { useI18n } from "../../i18n/LanguageProvider";
import { canOpenSupplierStore, canSupply } from "../wholesale/wholesale";
import { useAuth } from "../../store/auth";
import { billedPhrase, cycleUnit, includesStore, isUnlimited, planGroup, planTitle, termMonths } from "./planDisplay";

type PlanFilter = "all" | "tutorial" | "supplier";

function quota(value: unknown) {
  const count = Number(value);
  return Number.isFinite(count) ? count.toLocaleString("en-IN") : "—";
}

export function SubscriptionPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { user } = useAuth();
  const [params] = useSearchParams();
  const initialFilter = (params.get("filter") || "all").toLowerCase();
  const [filter, setFilter] = useState<PlanFilter>(
    initialFilter === "tutorial" || initialFilter === "supplier" ? initialFilter : "all",
  );

  const { data: current } = useQuery({
    queryKey: ["sub"],
    queryFn: async () => (await api.get("/subscriptions/current")).data.data as SubscriptionStatus,
  });
  const { data: plans } = useQuery({
    queryKey: ["plans"],
    queryFn: async () => (await api.get("/subscriptions/plans")).data.data as PlanCard[],
  });

  const locked = Boolean(current?.requiresRecharge);
  const grouped = useMemo(() => {
    const rows = (plans || []).filter((plan) => plan.purchasable);
    const by = (group: "seller" | "supplier" | "pro_plus" | "tutorial") =>
      rows
        .filter((plan) => planGroup(plan) === group)
        .sort((a, b) => termMonths(a) - termMonths(b) || Number(a.price) - Number(b.price));
    return {
      seller: by("seller"),
      supplier: by("supplier"),
      proPlus: by("pro_plus"),
      tutorial: by("tutorial"),
    };
  }, [plans]);

  function choose(name: string) {
    void qc.invalidateQueries({ queryKey: ["sub"] });
    navigate(`/subscription/pay?plan=${encodeURIComponent(name)}`);
  }

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

  const showSeller = filter === "all";
  const showSupplier = filter === "all" || filter === "supplier";
  const showTutorial = filter === "all" || filter === "tutorial";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">{t("app.subTitle")}</h1>
          <p className="text-slate-500">{statusLabel}</p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <span className="font-medium">{t("sub.filterLabel")}</span>
            <select
              value={filter}
              onChange={(e) => setFilter(e.target.value as PlanFilter)}
              className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-800"
            >
              <option value="all">{t("sub.filterAll")}</option>
              <option value="tutorial">{t("sub.filterTutorial")}</option>
              <option value="supplier">{t("sub.filterSupplier")}</option>
            </select>
          </label>
          <Link to="/transactions" className="text-sm font-medium text-teal-700 hover:underline">
            View transactions & bills
          </Link>
        </div>
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
                Open your store next. We verify the GSTIN and lock the store name to the GST trade name.
              </p>
            </div>
          </div>
          <Link to="/supplier/store" className="rounded-xl bg-teal-700 px-4 py-2.5 font-semibold text-white hover:bg-teal-800">
            Create / manage store
          </Link>
        </div>
      )}

      {showSeller && (
        <>
          <PlanSection
            title="Seller plans"
            subtitle="Monthly tools for listings, photos, labels, and tips."
            plans={grouped.seller}
            current={current}
            onChoose={choose}
            t={t}
          />
          <PlanSection
            title="PRO+ and BUSINESS"
            subtitle="Unlimited autofill, shoot photos, trending products, labels, and tips. PRO+ is one month without a store. BUSINESS half-year and yearly include a GST wholesale store."
            plans={grouped.proPlus}
            current={current}
            onChoose={choose}
            t={t}
          />
        </>
      )}
      {showSupplier && (
        <PlanSection
          title="Supplier"
          subtitle="GST-verified wholesale store. The same tools on every term, with the yearly plan at ₹1,199."
          plans={grouped.supplier}
          current={current}
          onChoose={choose}
          t={t}
        />
      )}
      {showTutorial && (
        <PlanSection
          title="Ecommerce tutorials"
          subtitle="Standalone monthly tutorial video library (₹1,999). Included only with the Tutorials plan — not with seller or supplier plans."
          plans={grouped.tutorial}
          current={current}
          onChoose={choose}
          t={t}
          tutorialOnly
        />
      )}
    </div>
  );
}

function PlanSection({
  title,
  subtitle,
  plans,
  current,
  onChoose,
  t,
  tutorialOnly,
}: {
  title: string;
  subtitle: string;
  plans: PlanCard[];
  current?: SubscriptionStatus;
  onChoose: (name: string) => void;
  t: (key: string, vars?: Record<string, string | number>) => string;
  tutorialOnly?: boolean;
}) {
  if (!plans.length) return null;
  return (
    <section className="space-y-3">
      <div>
        <h2 className="text-lg font-semibold">{title}</h2>
        <p className="text-sm text-slate-500">{subtitle}</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {plans.map((plan) => (
          <PlanCardView
            key={plan.name}
            plan={plan}
            current={current}
            onChoose={onChoose}
            t={t}
            tutorialOnly={tutorialOnly}
          />
        ))}
      </div>
    </section>
  );
}

function PlanCardView({
  plan,
  current,
  onChoose,
  t,
  tutorialOnly,
}: {
  plan: PlanCard;
  current?: SubscriptionStatus;
  onChoose: (name: string) => void;
  t: (key: string, vars?: Record<string, string | number>) => string;
  tutorialOnly?: boolean;
}) {
  const active = current?.plan === plan.name && current.accessEntitled;
  const store = includesStore(plan);
  const unlimited = isUnlimited(plan.features);
  const months = termMonths(plan);
  const monthly = months > 1 ? Math.round(Number(plan.price) / months) : null;
  const badge = typeof plan.features?.badge === "string" ? plan.features.badge : "";
  return (
    <article
      className={`flex flex-col rounded-2xl border p-5 ${
        active ? "border-teal-700 bg-teal-50" : "border-slate-200 bg-white"
      }`}
    >
      <div className="flex items-center justify-between gap-2">
        <h3 className="font-semibold">{planTitle(plan)}</h3>
        <div className="flex flex-wrap justify-end gap-1">
          {badge && (
            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[11px] font-semibold text-amber-900">
              {badge}
            </span>
          )}
          {store && (
            <span className="rounded-full bg-teal-100 px-2 py-0.5 text-[11px] font-semibold text-teal-800">
              Wholesale store
            </span>
          )}
        </div>
      </div>
      <p className="mt-2 text-3xl font-semibold">
        ₹{Number(plan.price).toLocaleString("en-IN")}
        <span className="ml-1 text-sm font-normal text-slate-500">/ {cycleUnit(plan.billingCycle)}</span>
      </p>
      <p className="mt-1 text-xs text-slate-500">
        {monthly ? `₹${monthly.toLocaleString("en-IN")} a month, ${billedPhrase(plan.billingCycle)}` : "Taxes and promo applied on the next step"}
      </p>
      {monthly && <p className="mt-1 text-xs text-slate-500">Taxes and promo applied on the next step</p>}
      <ul className="mt-4 flex-1 space-y-1 text-sm text-slate-600">
        {tutorialOnly ? (
          <>
            <li>{t("sub.ecommerceTutorials")}</li>
            <li>Q&amp;A comments on each video</li>
          </>
        ) : (
          <>
            {unlimited ? (
              <>
                <li>Unlimited autofill</li>
                <li>Unlimited shoot photos</li>
                <li>Unlimited trending products</li>
              </>
            ) : (
              <>
                <li>{t("sub.aiMonth", { n: quota(plan.features?.monthlyAiAnalyses) })}</li>
                <li>{t("sub.shootMonth", { n: quota(plan.features?.monthlyShootPhotos) })}</li>
                <li>{t("sub.trendingMonth", { n: quota(plan.features?.monthlyTrendingProducts) })}</li>
              </>
            )}
            <li>{t("sub.labelUnlimited")}</li>
            {store && <li className="font-medium text-teal-800">GST-verified wholesale store</li>}
          </>
        )}
      </ul>
      <button
        type="button"
        onClick={() => onChoose(plan.name)}
        className="mt-5 rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800"
      >
        {active ? t("sub.extend") : t("sub.rechargePlan")}
      </button>
    </article>
  );
}
