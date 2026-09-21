import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Copy, MessageCircle, ShieldAlert, Smartphone } from "lucide-react";
import { useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import { useAuth } from "../../store/auth";
import type { PaymentCheckout, PlanCard, SubscriptionStatus } from "../../types";
import { useI18n } from "../../i18n/LanguageProvider";
import { formatWhatsapp, whatsappDigits } from "../site/whatsapp";

const DEFAULT_SCANNER = "/payment-qr.jpg";

export function SubscriptionPage() {
  const { t } = useI18n();
  const { user } = useAuth();
  const qc = useQueryClient();
  const [params] = useSearchParams();
  const requested = (params.get("plan") || "").toUpperCase();
  const [selected, setSelected] = useState(requested);
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

  const checkout = useMutation({
    mutationFn: async (name: string) =>
      (await api.post(`/subscriptions/plans/${name}/checkout`)).data.data as PaymentCheckout,
    onSuccess: (_data, name) => {
      setSelected(name);
      setAck("");
    },
  });
  const paymentSent = useMutation({
    mutationFn: async (name: string) => api.post(`/subscriptions/plans/${name}/payment-sent`),
    onSuccess: async () => {
      setAck(t("sub.ack"));
      await qc.invalidateQueries({ queryKey: ["sub"] });
    },
  });

  const payment = checkout.data;
  const email = payment?.registeredEmail || current?.registeredEmail || user?.email || "";
  const locked = Boolean(current?.requiresRecharge);
  const scannerSrc = payment?.qrImageUrl || current?.paymentNotice?.qrImageUrl || DEFAULT_SCANNER;
  const payee = payment?.payeeName || current?.paymentNotice?.payeeName || "VISHAL KUMAR MISHRA";
  const whatsappNumber = payment?.whatsappNumber || current?.paymentNotice?.whatsappNumber || "";
  const whatsappDigitsValue = whatsappDigits(whatsappNumber);
  const whatsappDisplay = formatWhatsapp(whatsappNumber);
  const whatsappLocal =
    whatsappDigitsValue.startsWith("91") && whatsappDigitsValue.length === 12
      ? whatsappDigitsValue.slice(2)
      : whatsappDigitsValue;

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

  async function copyText(value: string, key: string) {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(key);
      window.setTimeout(() => setCopied(""), 1600);
    } catch {
      setCopied("");
    }
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">{t("app.subTitle")}</h1>
        <p className="text-slate-500">{statusLabel}</p>
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
          <p className="mt-2 text-sm leading-relaxed">
            {current?.rechargeBody}
          </p>
        </div>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {(plans || [])
          .filter((plan) => plan.purchasable)
          .map((plan) => {
            const active = current?.plan === plan.name && current.accessEntitled;
            const chosen = selected === plan.name || payment?.plan === plan.name;
            return (
              <article
                key={plan.name}
                className={`flex flex-col rounded-2xl border p-5 ${
                  chosen || active ? "border-teal-700 bg-teal-50" : "border-slate-200 bg-white"
                }`}
              >
                <h3 className="font-semibold">{plan.name}</h3>
                <p className="mt-2 text-3xl font-semibold">
                  ₹{Number(plan.price)}
                  <span className="ml-1 text-sm font-normal text-slate-500">/ {plan.billingCycle?.toLowerCase()}</span>
                </p>
                <ul className="mt-4 flex-1 space-y-1 text-sm text-slate-600">
                  <li>{t("sub.aiMonth", { n: String(plan.features?.monthlyAiAnalyses ?? "—") })}</li>
                  <li>{t("sub.products", { n: String(plan.features?.products ?? "—") })}</li>
                  <li>{t("sub.devices", { n: String(plan.features?.extensionDevices ?? "—") })}</li>
                  <li>{String(plan.features?.labelCrop ?? t("sub.labelCrop"))}</li>
                </ul>
                <button
                  type="button"
                  disabled={checkout.isPending}
                  onClick={() => checkout.mutate(plan.name)}
                  className="mt-5 rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-medium text-white hover:bg-teal-800 disabled:opacity-60"
                >
                  {active ? t("sub.extend") : t("sub.rechargePlan")}
                </button>
              </article>
            );
          })}
      </div>

      {checkout.isError && (
        <p className="text-sm text-red-600">{apiErrorMessage(checkout.error, t("sub.payFail"))}</p>
      )}

      <section className="grid gap-6 rounded-2xl border border-slate-200 bg-white p-6 lg:grid-cols-[280px_1fr]">
        <div className="rounded-2xl bg-black p-3 text-center">
          <p className="text-xs font-semibold uppercase tracking-wide text-violet-300">{t("sub.scan")}</p>
          <img
            src={scannerSrc}
            alt={`Payment scanner for ${payee}`}
            className="mx-auto mt-3 w-full max-w-[240px] rounded-xl object-contain"
          />
          <p className="mt-3 text-sm font-medium text-white">{payee}</p>
        </div>
        <div className="space-y-4">
          <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4">
            <p className="flex items-center gap-2 font-semibold text-amber-950">
              <MessageCircle size={18} />
              {t("sub.whatsapp")}
            </p>
            <p className="mt-2 text-sm leading-relaxed text-amber-950">
              {payment?.notice || current?.paymentNotice?.instructions}
            </p>
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
                <p className="text-xs text-amber-900">{t("sub.waManual")}</p>
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
          <p className="text-sm text-slate-600">{payment?.instructions || current?.paymentNotice?.instructions}</p>
          {payment && (
            <dl className="grid gap-2 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-slate-500">{t("sub.plan")}</dt>
                <dd className="font-medium">{payment.plan} · ₹{payment.amount}</dd>
              </div>
              <div>
                <dt className="text-slate-500">{t("sub.payee")}</dt>
                <dd className="font-medium">{payment.payeeName}</dd>
              </div>
            </dl>
          )}
          <div className="flex flex-wrap gap-3">
            <a
              href={payment?.whatsappUrl || whatsappLink(whatsappNumber, email)}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-2 rounded-xl bg-[#128C7E] px-4 py-2.5 text-sm font-medium text-white"
            >
              <Smartphone size={16} />
              {t("sub.openWa")}
            </a>
            {payment && (
              <button
                type="button"
                disabled={paymentSent.isPending}
                onClick={() => paymentSent.mutate(payment.plan)}
                className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-medium"
              >
                {paymentSent.isPending ? t("app.extSaving") : t("sub.sent")}
              </button>
            )}
          </div>
          {ack && <p className="text-sm text-teal-800">{ack}</p>}
          {paymentSent.isError && (
            <p className="text-sm text-red-600">{apiErrorMessage(paymentSent.error)}</p>
          )}
        </div>
      </section>
    </div>
  );
}

function whatsappLink(number: string | undefined, email: string) {
  const digits = (number || "").replace(/\D/g, "");
  const text = encodeURIComponent(
    `Hello Catalog Studio, I have paid for a plan. Registered email: ${email}. Payment screenshot is attached.`,
  );
  return digits ? `https://wa.me/${digits}?text=${text}` : `https://wa.me/?text=${text}`;
}
