import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ShieldCheck } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import { useAuth } from "../../store/auth";
import type { BillingPromoOption, PaymentCheckout, PriceBreakdown, PlanCard, SubscriptionStatus } from "../../types";
import { openCashfreeCheckout } from "./cashfreeCheckout";
import { billedPhrase, planTitle } from "./planDisplay";

function inr(value?: number | null) {
  return `₹${Number(value || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function SubscriptionPaymentPage() {
  const { user } = useAuth();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const planName = (params.get("plan") || "").toUpperCase();
  const [promoCode, setPromoCode] = useState("");
  const [customerPhone, setCustomerPhone] = useState("");
  const [launchError, setLaunchError] = useState("");

  const { data: current } = useQuery({
    queryKey: ["sub"],
    queryFn: async () => (await api.get("/subscriptions/current")).data.data as SubscriptionStatus,
  });
  const { data: plans } = useQuery({
    queryKey: ["plans"],
    queryFn: async () => (await api.get("/subscriptions/plans")).data.data as PlanCard[],
  });
  const { data: promos } = useQuery({
    queryKey: ["billing-promos-active"],
    queryFn: async () => (await api.get("/subscriptions/promos")).data.data as BillingPromoOption[],
  });

  const plan = useMemo(
    () => (plans || []).find((row) => row.name.toUpperCase() === planName),
    [plans, planName],
  );

  const quote = useQuery({
    queryKey: ["quote", planName, promoCode],
    enabled: Boolean(planName),
    queryFn: async () =>
      (
        await api.post(`/subscriptions/plans/${planName}/quote`, {
          promoCode: promoCode || null,
        })
      ).data.data as PriceBreakdown,
  });

  const checkout = useMutation({
    mutationFn: async () =>
      (
        await api.post(`/subscriptions/plans/${planName}/checkout`, {
          promoCode: promoCode || null,
          customerPhone: customerPhone || null,
          returnOrigin: window.location.origin,
        })
      ).data.data as PaymentCheckout,
    onSuccess: async (payment) => {
      setLaunchError("");
      await qc.invalidateQueries({ queryKey: ["sub"] });
      await qc.invalidateQueries({ queryKey: ["transactions"] });
      const sessionId =
        payment.paymentSessionId ||
        (payment.checkoutUrl?.startsWith("cashfree-session:")
          ? payment.checkoutUrl.slice("cashfree-session:".length)
          : "");
      if (payment.provider === "CASHFREE" && sessionId) {
        try {
          await openCashfreeCheckout(sessionId, "production");
          return;
        } catch (err) {
          setLaunchError(err instanceof Error ? err.message : "Could not open Cashfree checkout");
          if (payment.sessionId) {
            navigate(`/subscription/payment-result?order_id=${encodeURIComponent(payment.sessionId)}`);
          }
          return;
        }
      }
      if (payment.sessionId) {
        navigate(`/subscription/payment-result?order_id=${encodeURIComponent(payment.sessionId)}`);
      }
    },
  });

  useEffect(() => {
    if (!planName) navigate("/subscription", { replace: true });
  }, [planName, navigate]);

  const pricing = checkout.data?.pricing || quote.data;
  const profileMobile = (user as { mobile?: string } | null)?.mobile || "";

  if (!planName) return null;

  return (
    <div className="space-y-6">
      <div>
        <Link to="/subscription" className="inline-flex items-center gap-1 text-sm font-medium text-teal-700 hover:underline">
          <ArrowLeft size={14} /> Back to plans
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">Checkout · {plan ? planTitle(plan) : planName}</h1>
        <p className="text-slate-500">
          Review price, apply a promo if you have one, then pay securely with Cashfree. Your plan activates automatically
          after successful payment.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6">
          <div>
            <h2 className="font-semibold">Order summary</h2>
            <p className="mt-1 text-sm text-slate-500">
              {plan ? `${planTitle(plan)} · ${billedPhrase(plan.billingCycle)}` : "Loading plan…"}
            </p>
          </div>

          <label className="block text-sm font-medium">
            Promo code
            <select
              value={promoCode}
              onChange={(event) => {
                setPromoCode(event.target.value);
                checkout.reset();
              }}
              className="mt-1 w-full rounded-xl border px-3 py-2.5"
            >
              <option value="">No promo code</option>
              {(promos || []).map((promo) => (
                <option key={promo.code} value={promo.code}>
                  {promo.code}
                  {promo.description ? ` — ${promo.description}` : ""}
                  {promo.discountType === "PERCENT"
                    ? ` (${Number(promo.discountValue)}% off)`
                    : ` (₹${Number(promo.discountValue)} off)`}
                </option>
              ))}
            </select>
          </label>

          {!profileMobile && (
            <label className="block text-sm font-medium">
              Mobile number (required for payment)
              <input
                value={customerPhone}
                onChange={(event) => setCustomerPhone(event.target.value.replace(/[^\d+]/g, "").slice(0, 13))}
                placeholder="10-digit Indian mobile"
                className="mt-1 w-full rounded-xl border px-3 py-2.5"
                inputMode="tel"
              />
            </label>
          )}

          {quote.isError && (
            <p className="text-sm text-red-600">{apiErrorMessage(quote.error, "Could not apply promo")}</p>
          )}

          <PriceRows pricing={pricing} loading={quote.isLoading} />

          <button
            type="button"
            disabled={checkout.isPending || !planName || quote.isError}
            onClick={() => checkout.mutate()}
            className="w-full rounded-xl bg-teal-700 px-4 py-3 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-60"
          >
            {checkout.isPending ? "Opening Cashfree…" : `Pay ${inr(pricing?.totalAmount)} securely`}
          </button>
          {checkout.isError && (
            <p className="text-sm text-red-600">{apiErrorMessage(checkout.error, "Could not start payment")}</p>
          )}
          {launchError && <p className="text-sm text-amber-700">{launchError}</p>}
        </section>

        <section className="rounded-2xl border border-slate-200 bg-slate-50 p-6">
          <div className="flex h-full min-h-[280px] flex-col justify-center gap-4">
            <div className="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-teal-100 text-teal-800">
              <ShieldCheck size={24} />
            </div>
            <h2 className="text-lg font-semibold text-slate-900">Secure online payment</h2>
            <ul className="space-y-2 text-sm text-slate-600">
              <li>UPI, cards, and netbanking via Cashfree Payment Gateway.</li>
              <li>No QR screenshots or WhatsApp confirmation needed.</li>
              <li>Plan unlocks automatically when Cashfree confirms payment.</li>
              <li>Tax invoice appears in Transactions after activation.</li>
            </ul>
            {current?.registeredEmail && (
              <p className="text-xs text-slate-500">Billed to {current.registeredEmail}</p>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

function PriceRows({ pricing, loading }: { pricing?: PriceBreakdown; loading?: boolean }) {
  if (loading || !pricing) {
    return <p className="text-sm text-slate-500">Calculating taxes…</p>;
  }
  return (
    <dl className="space-y-2 rounded-xl border border-slate-100 bg-slate-50 px-4 py-3 text-sm">
      <Row label="Plan amount" value={inr(pricing.baseAmount)} />
      {Number(pricing.discountAmount) > 0 && (
        <Row
          label={pricing.promoCode ? `Discount (${pricing.promoCode})` : "Discount"}
          value={`−${inr(pricing.discountAmount)}`}
          accent
        />
      )}
      <Row
        label={`Service charge${Number(pricing.serviceChargePercent) ? ` (${pricing.serviceChargePercent}%)` : ""}`}
        value={inr(pricing.serviceCharge)}
      />
      <Row
        label={`GST${Number(pricing.gstPercent) ? ` (${pricing.gstPercent}%)` : ""}`}
        value={inr(pricing.gstAmount)}
      />
      <div className="flex items-center justify-between border-t border-slate-200 pt-2 font-semibold">
        <dt>Total payable</dt>
        <dd className="text-lg text-teal-800">{inr(pricing.totalAmount)}</dd>
      </div>
      {pricing.companyLegalName && (
        <p className="pt-1 text-xs text-slate-500">
          Billed by {pricing.companyLegalName}
          {pricing.parentCompanyName ? ` · ${pricing.parentCompanyName}` : ""}
          {pricing.companyGstin ? ` · GSTIN ${pricing.companyGstin}` : ""}
        </p>
      )}
    </dl>
  );
}

function Row({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-slate-600">{label}</dt>
      <dd className={accent ? "font-medium text-amber-700" : "font-medium"}>{value}</dd>
    </div>
  );
}
