import { Link } from "react-router-dom";
import { EnquiryForm } from "./EnquiryForm";
import { PublicToolShell } from "./MarketingLayout";
import { useSite } from "./useSite";
import { useI18n } from "../../i18n/LanguageProvider";

export function ExtensionMarketingPage() {
  const site = useSite();
  const { t } = useI18n();
  return (
    <PublicToolShell>
      <div className="mx-auto max-w-3xl rounded-3xl border border-slate-200 bg-white p-6 shadow-sm md:p-8">
        <h1 className="text-3xl font-semibold">{t("extm.title")}</h1>
        <p className="mt-3 text-sm leading-relaxed text-slate-600">
          {t("extm.body")}
          {site.client ? ` ${site.client.storeName}` : ""}
        </p>
        <ol className="mt-5 list-decimal space-y-2 pl-5 text-sm text-slate-600">
          <li>{t("extm.step1")}</li>
          <li>{t("extm.step2")}</li>
          <li>{t("extm.step3")}</li>
          <li>{t("extm.step4")}</li>
        </ol>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link to="/login" className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold">
            {t("login.submit")}
          </Link>
          <Link to="/register" className="rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white">
            {t("register.submit")}
          </Link>
        </div>
      </div>
    </PublicToolShell>
  );
}

export function ContactPage() {
  const site = useSite();
  const { t } = useI18n();
  return (
    <PublicToolShell>
      <div className="mx-auto max-w-3xl space-y-6">
        <div>
          <h1 className="text-3xl font-semibold">{t("contact.title")}</h1>
          <p className="mt-2 text-slate-600">
            {t("contact.body", { email: site.support.email })}
            {site.support.phone ? ` ${site.support.phone}` : ""}
          </p>
        </div>
        <EnquiryForm />
      </div>
    </PublicToolShell>
  );
}

export function PrivacyPolicyPage() {
  const site = useSite();
  return (
    <PublicToolShell>
      <article className="mx-auto max-w-3xl space-y-5 rounded-3xl border border-slate-200 bg-white p-6 text-sm leading-relaxed text-slate-700 shadow-sm md:p-8">
        <h1 className="text-3xl font-semibold text-slate-900">Privacy policy</h1>
        <p>Last updated: 20 September 2026</p>
        <p>
          Catalog Studio ({site.branding.siteName}) provides a seller workspace at{" "}
          <a className="font-medium text-teal-700" href="https://www.catalogstudio.in">
            www.catalogstudio.in
          </a>{" "}
          and the Catalog Studio Autofill Chrome extension. This page describes what the extension and website collect
          and how that data is used.
        </p>
        <h2 className="text-lg font-semibold text-slate-900">What the Chrome extension accesses</h2>
        <ul className="list-disc space-y-2 pl-5">
          <li>Your Catalog Studio workspace after you log in or paste a pairing key (account name, email, business profile used to fill listings).</li>
          <li>Product photos and listing fields you choose to generate or fill on Meesho supplier pages.</li>
          <li>The Meesho store name shown in your seller account so the workspace can remember it.</li>
        </ul>
        <h2 className="text-lg font-semibold text-slate-900">What we do not do</h2>
        <ul className="list-disc space-y-2 pl-5">
          <li>We never auto-submit a marketplace listing. You review filled fields and submit yourself.</li>
          <li>We do not store marketplace passwords.</li>
          <li>We do not sell personal data.</li>
        </ul>
        <h2 className="text-lg font-semibold text-slate-900">Storage and sharing</h2>
        <p>
          Pairing keys and API settings are stored in Chrome local storage on your device. Listing data is sent to the
          Catalog Studio API at www.catalogstudio.in so your workspace can generate and save catalog values. Host
          access is limited to Catalog Studio and Meesho pages required for autofill.
        </p>
        <h2 className="text-lg font-semibold text-slate-900">Contact</h2>
        <p>
          Questions:{" "}
          <a className="font-medium text-teal-700" href={`mailto:${site.support.email}`}>
            {site.support.email}
          </a>
        </p>
      </article>
    </PublicToolShell>
  );
}
