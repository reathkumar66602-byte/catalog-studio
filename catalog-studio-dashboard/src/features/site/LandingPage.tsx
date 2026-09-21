import { useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Calculator, Chrome, Copy, Scissors } from "lucide-react";
import { FlipkartLogo, MeeshoLogo, MergePdfLogo } from "../labels/logos";
import { EnquiryForm } from "./EnquiryForm";
import { HeroIllustration } from "./HeroIllustration";
import { formatMoney } from "./format";
import { useSite } from "./useSite";
import { useI18n } from "../../i18n/LanguageProvider";

export function LandingPage() {
  const site = useSite();
  const { t } = useI18n();
  const heroBg = site.branding.heroBackground || "#07111f";
  const accent = site.client?.branding?.accentColor;
  const accentColor = typeof accent === "string" ? accent : site.branding.accentColor || "#38bdf8";

  return (
    <div>
      <section className="relative overflow-hidden text-white" style={{ background: heroBg }}>
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_12%_20%,rgba(45,212,191,0.22),transparent_36%),radial-gradient(circle_at_92%_8%,rgba(56,189,248,0.2),transparent_34%)]" />
        <div className="relative mx-auto max-w-6xl px-4 py-16 lg:py-20">
          <div className="grid items-center gap-10 lg:grid-cols-[1.05fr_0.95fr]">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.22em] text-teal-300">{t("land.tagline")}</p>
              <h1 className="mt-3 max-w-xl text-4xl font-semibold leading-tight tracking-tight md:text-5xl">{t("land.heroTitle")}</h1>
              <p className="mt-5 max-w-xl text-sm leading-relaxed text-slate-300 md:text-base">{t("land.heroSub")}</p>
              <div className="mt-8 flex flex-wrap gap-3">
                <Link
                  to="/login"
                  className="rounded-full bg-teal-400 px-5 py-2.5 text-sm font-semibold text-slate-950 hover:bg-teal-300"
                >
                  {t("login.submit")}
                </Link>
                <Link
                  to="/register"
                  className="rounded-full border border-white/20 bg-white/5 px-5 py-2.5 text-sm font-semibold text-white hover:bg-white/10"
                >
                  {t("register.submit")}
                </Link>
              </div>
            </div>
            <HeroIllustration />
          </div>
          <div className="mt-12 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
            <ServiceChip logo={<FlipkartLogo className="h-11 w-11" />} label={t("land.fkCrop")} hint={t("land.fkHint")} />
            <ServiceChip logo={<MeeshoLogo className="h-11 w-11" />} label={t("land.meCrop")} hint={t("land.meHint")} />
            <ServiceChip logo={<MergePdfLogo className="h-11 w-11" />} label={t("land.merge")} hint={t("land.mergeHint")} />
            <ServiceChip logo={<CalcMark />} label={t("land.profit")} hint={t("land.profitHint")} />
            <ServiceChip logo={<ExtMark />} label={t("land.extChip")} hint={t("land.extHint")} />
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 py-14">
        <h2 className="text-2xl font-semibold">{t("land.offers")}</h2>
        <p className="mt-2 max-w-2xl text-slate-600">{t("land.offersSub")}</p>
        <div className="mt-6 grid gap-4 md:grid-cols-3">
          <Feature icon={Scissors} title={t("land.featCrop")} text={t("land.featCropText")} />
          <Feature icon={Calculator} title={t("land.featCalc")} text={t("land.featCalcText")} />
          <Feature icon={Chrome} title={t("land.featExt")} text={t("land.featExtText")} />
        </div>
      </section>

      {site.client && (
        <section className="bg-slate-50">
          <div className="mx-auto grid max-w-6xl gap-8 px-4 py-14 lg:grid-cols-[1.2fr_0.8fr]">
            <div>
              <p className="text-sm font-semibold uppercase tracking-[0.2em] text-teal-700">{t("land.featured")}</p>
              <h2 className="mt-2 text-3xl font-semibold">{site.client.storeName}</h2>
              <p className="mt-2 text-lg text-slate-600">{t("land.clientTagline")}</p>
              <p className="mt-4 max-w-2xl text-slate-600">{t("land.clientAbout")}</p>
              <dl className="mt-6 grid gap-3 text-sm sm:grid-cols-2">
                {site.client.ownerName && <Item label={t("land.owner")} value={site.client.ownerName} />}
                {site.client.email && <Item label={t("land.storeEmail")} value={site.client.email} />}
                {site.client.phone && <Item label={t("land.phone")} value={site.client.phone} />}
                {site.client.address && <Item label={t("land.address")} value={site.client.address} />}
              </dl>
            </div>
            <div className="space-y-3">
              {site.promoCodes.map((promo) => (
                <PromoCard
                  key={promo.code}
                  code={promo.code}
                  headline={t("land.promoHeadline")}
                  description={t("land.promoDesc")}
                  discountType={promo.discountType}
                  discountValue={promo.discountValue}
                  trialDays={promo.trialDays}
                  accent={accentColor}
                />
              ))}
            </div>
          </div>
        </section>
      )}

      <section className="mx-auto max-w-3xl px-4 py-14">
        <EnquiryForm />
      </section>
    </div>
  );
}

function ServiceChip({ logo, label, hint }: { logo: ReactNode; label: string; hint: string }) {
  return (
    <div className="flex items-center gap-3 rounded-2xl bg-white px-4 py-3.5 text-slate-900 shadow-[0_10px_30px_-18px_rgba(15,23,42,0.45)] ring-1 ring-white/70 xl:flex-col xl:items-start">
      {logo}
      <div>
        <p className="text-sm font-semibold leading-snug">{label}</p>
        <p className="mt-1 text-xs leading-snug text-slate-500">{hint}</p>
      </div>
    </div>
  );
}

function Feature({
  icon: Icon,
  title,
  text,
}: {
  icon: typeof Scissors;
  title: string;
  text: string;
}) {
  return (
    <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
      <Icon className="text-teal-700" size={22} />
      <h3 className="mt-3 text-lg font-semibold">{title}</h3>
      <p className="mt-2 text-sm text-slate-600">{text}</p>
    </div>
  );
}

function Item({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-slate-500">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}

function PromoCard({
  code,
  headline,
  description,
  discountType,
  discountValue,
  trialDays = 0,
  accent,
}: {
  code: string;
  headline?: string;
  description?: string;
  discountType: string;
  discountValue: number;
  trialDays?: number;
  accent: string;
}) {
  const { t } = useI18n();
  const [copied, setCopied] = useState(false);
  const offer =
    discountType === "TRIAL"
      ? t("land.promoTrial", { days: trialDays || discountValue })
      : discountType === "FIXED"
        ? t("land.promoFixed", { amount: formatMoney(discountValue) })
        : t("land.promoOff", { value: discountValue });
  return (
    <div className="rounded-3xl border border-slate-200 bg-white p-6" style={{ borderTopColor: accent, borderTopWidth: 4 }}>
      <p className="text-sm font-medium text-slate-500">{headline || t("land.storePromo")}</p>
      <p className="mt-1 font-mono text-2xl font-semibold tracking-wide">{code}</p>
      <p className="mt-1 text-sm text-teal-800">{offer}</p>
      {description && <p className="mt-2 text-sm text-slate-600">{description}</p>}
      <button
        type="button"
        className="mt-4 inline-flex items-center gap-2 rounded-xl border border-slate-200 px-3 py-2 text-sm"
        onClick={async () => {
          await navigator.clipboard.writeText(code);
          setCopied(true);
        }}
      >
        <Copy size={14} />
        {copied ? t("land.copied") : t("land.copyCode")}
      </button>
    </div>
  );
}

function CalcMark() {
  return (
    <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-teal-700 text-white">
      <Calculator size={18} />
    </div>
  );
}

function ExtMark() {
  return (
    <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-sky-800 text-white">
      <Chrome size={18} />
    </div>
  );
}
