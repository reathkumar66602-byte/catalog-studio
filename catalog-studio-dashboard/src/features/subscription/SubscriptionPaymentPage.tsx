import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Check, Copy, MessageCircle, Smartphone } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import { useAuth } from "../../store/auth";
import type { BillingPromoOption, PaymentCheckout, PriceBreakdown, PlanCard, SubscriptionStatus } from "../../types";
import { useI18n } from "../../i18n/LanguageProvider";
import { formatWhatsapp, whatsappDigits } from "../site/whatsapp";

const DEFAULT_SCANNER = "/payment-qr.jpg";

function inr(value?: number | null) {
  return `₹${Number(value || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function SubscriptionPaymentPage() {
  const { t } = useI18n();
  const { user } = useAuth();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const planName = (params.get("plan") || "").toUpperCase();
  const [promoCode, setPromoCode] = useState("");
  const [showPay, setShowPay] = useState(false);
  const [copied, setCopied] = useState("");
  const [ack, setAck] = useState("");

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
        })
      ).data.data as PaymentCheckout,
    onSuccess: async () => {
      setShowPay(true);
      setAck("");
      await qc.invalidateQueries({ queryKey: ["sub"] });
      await qc.invalidateQueries({ queryKey: ["transactions"] });
    },
  });

  const paymentSent = useMutation({
    mutationFn: async () => api.post(`/subscriptions/plans/${planName}/payment-sent`),
    onSuccess: async () => {
      setAck(t("sub.ack"));
      await qc.invalidateQueries({ queryKey: ["sub"] });
      await qc.invalidateQueries({ queryKey: ["transactions"] });
    },
  });

  useEffect(() => {
    if (!planName) navigate("/subscription", { replace: true });
  }, [planName, navigate]);

  const payment = checkout.data;
  const pricing = payment?.pricing || quote.data;
  const email = payment?.registeredEmail || current?.registeredEmail || user?.email || "";
  const scannerSrc = payment?.qrImageUrl || DEFAULT_SCANNER;
  const payee = payment?.payeeName || "VISHAL KUMAR MISHRA";
  const whatsappNumber = payment?.whatsappNumber || "";
  const whatsappDigitsValue = whatsappDigits(whatsappNumber);
  const whatsappDisplay = formatWhatsapp(whatsappNumber);

  async function copyText(value: string, key: string) {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(key);
      window.setTimeout(() => setCopied(""), 1600);
    } catch {
      setCopied("");
    }
  }

  if (!planName) return null;

  return (
    <div className="space-y-6">
      <div>
        <Link to="/subscription" className="inline-flex items-center gap-1 text-sm font-medium text-teal-700 hover:underline">
          <ArrowLeft size={14} /> Back to plans
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">Checkout · {planName}</h1>
        <p className="text-slate-500">
          Review price, apply a promo, then pay. GST invoice details appear on your bill after activation.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
        <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6">
          <div>
            <h2 className="font-semibold">Order summary</h2>
            <p className="mt-1 text-sm text-slate-500">
              {plan ? `${plan.name} · billed ${plan.billingCycle?.toLowerCase()}` : "Loading plan…"}
            </p>
          </div>

          <label className="block text-sm font-medium">
            Promo code
            <select
              value={promoCode}
              onChange={(event) => {
                setPromoCode(event.target.value);
                setShowPay(false);
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

          {quote.isError && (
            <p className="text-sm text-red-600">{apiErrorMessage(quote.error, "Could not apply promo")}</p>
          )}

          <PriceRows pricing={pricing} loading={quote.isLoading} />

          {!showPay && (
            <button
              type="button"
              disabled={checkout.isPending || !planName || quote.isError}
              onClick={() => checkout.mutate()}
              className="w-full rounded-xl bg-teal-700 px-4 py-3 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-60"
            >
              {checkout.isPending ? "Preparing payment…" : `Pay ${inr(pricing?.totalAmount)}`}
            </button>
          )}
          {checkout.isError && (
            <p className="text-sm text-red-600">{apiErrorMessage(checkout.error, t("sub.payFail"))}</p>
          )}
        </section>

        <section className="rounded-2xl border border-slate-200 bg-slate-50 p-6">
          {!showPay || !payment ? (
            <div className="flex h-full min-h-[280px] flex-col items-center justify-center text-center text-sm text-slate-500">
              <p className="max-w-xs">
                Choose a promo if you have one, confirm the total, then click <span className="font-semibold text-slate-700">Pay</span> to
                reveal the PhonePe QR and WhatsApp instructions.
              </p>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="rounded-2xl bg-black p-3 text-center">
                <p className="text-xs font-semibold uppercase tracking-wide text-violet-300">{t("sub.scan")}</p>
                <img
                  src={scannerSrc}
                  alt={`Payment scanner for ${payee}`}
                  className="mx-auto mt-3 w-full max-w-[240px] rounded-xl object-contain"
                />
                <p className="mt-3 text-sm font-medium text-white">{payee}</p>
                <p className="mt-1 text-xs text-slate-300">Pay exactly {inr(payment.amount)}</p>
              </div>

              <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4">
                <p className="flex items-center gap-2 font-semibold text-amber-950">
                  <MessageCircle size={18} />
                  {t("sub.whatsapp")}
                </p>
                <p className="mt-2 text-sm leading-relaxed text-amber-950">{payment.notice}</p>
                {whatsappDisplay && (
                  <div className="mt-3 space-y-2">
                    <p className="text-sm font-medium text-amber-950">{t("sub.waNumber")}</p>
                    <div className="flex flex-wrap items-center gap-2">
                      <a
                        className="rounded-lg bg-white px-3 py-1.5 text-sm font-semibold text-teal-800"
                        href={`https://wa.me/${whatsappDigitsValue}`}
                        target="_blank"
                        rel="noreferrer"
                      >
                        {whatsappDisplay}
                      </a>
                      <button
                        type="button"
                        onClick={() => copyText(whatsappDigitsValue, "whatsapp")}
                        className="inline-flex items-center gap-1 rounded-lg border border-amber-300 bg-white px-2.5 py-1.5 text-xs font-medium"
                      >
                        {copied === "whatsapp" ? <Check size={14} /> : <Copy size={14} />}
                        {copied === "whatsapp" ? t("common.copied") : t("sub.copyNumber")}
                      </button>
                    </div>
                  </div>
                )}
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <code className="rounded-lg bg-white px-3 py-1.5 text-sm font-semibold text-teal-800">{email}</code>
                  <button
                    type="button"
                    onClick={() => copyText(email, "email")}
                    className="inline-flex items-center gap-1 rounded-lg border border-amber-300 bg-white px-2.5 py-1.5 text-xs font-medium"
                  >
                    {copied === "email" ? <Check size={14} /> : <Copy size={14} />}
                    {copied === "email" ? t("common.copied") : t("sub.copyEmail")}
                  </button>
                </div>
              </div>

              <p className="text-sm text-slate-600">{payment.instructions}</p>

              {pricing?.companyGstin && (
                <p className="text-xs text-slate-500">Company GSTIN: {pricing.companyGstin}</p>
              )}

              <div className="flex flex-wrap gap-3">
                <a
                  href={payment.whatsappUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-2 rounded-xl bg-[#128C7E] px-4 py-2.5 text-sm font-medium text-white"
                >
                  <Smartphone size={16} />
                  {t("sub.openWa")}
                </a>
                <button
                  type="button"
                  disabled={paymentSent.isPending}
                  onClick={() => paymentSent.mutate()}
                  className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-medium"
                >
                  {paymentSent.isPending ? t("app.extSaving") : t("sub.sent")}
                </button>
              </div>
              {ack && <p className="text-sm text-teal-800">{ack}</p>}
              {paymentSent.isError && (
                <p className="text-sm text-red-600">{apiErrorMessage(paymentSent.error)}</p>
              )}
            </div>
          )}
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
