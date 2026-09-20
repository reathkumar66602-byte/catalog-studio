import { Link } from "react-router-dom";
import { EnquiryForm } from "./EnquiryForm";
import { PublicToolShell } from "./MarketingLayout";
import { useSite } from "./useSite";

export function ExtensionMarketingPage() {
  const site = useSite();
  return (
    <PublicToolShell>
      <div className="mx-auto max-w-3xl rounded-3xl border border-slate-200 bg-white p-6 shadow-sm md:p-8">
        <h1 className="text-3xl font-semibold">Catalog Studio Chrome extension</h1>
        <p className="mt-3 text-sm leading-relaxed text-slate-600">
          Pair the extension with your workspace to fill GST, HSN, manufacturer, packer, and product fields on Meesho.
          {site.client ? ` ${site.client.storeName} sellers can lock the shop name after the first successful read.` : ""} Catalog
          Studio never submits the listing for you.
        </p>
        <ol className="mt-5 list-decimal space-y-2 pl-5 text-sm text-slate-600">
          <li>Create a free Catalog Studio account.</li>
          <li>
            Install Catalog Studio Autofill from the Chrome Web Store, or for local testing load unpacked from
            catalog-studio-extension/dist.
          </li>
          <li>Stay logged in to the workspace so pairing happens automatically.</li>
          <li>Open Meesho Add Single Catalog and use Generate, then Fill Values for Form.</li>
        </ol>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link to="/login" className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold">
            Login
          </Link>
          <Link to="/register" className="rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white">
            Register
          </Link>
        </div>
      </div>
    </PublicToolShell>
  );
}

export function ContactPage() {
  const site = useSite();
  return (
    <PublicToolShell>
      <div className="mx-auto max-w-3xl space-y-6">
        <div>
          <h1 className="text-3xl font-semibold">Contact and support</h1>
          <p className="mt-2 text-slate-600">
            Email{" "}
            <a className="font-medium text-teal-700" href={`mailto:${site.support.email}`}>
              {site.support.email}
            </a>
            {site.support.phone ? ` or call ${site.support.phone}.` : "."} Both the support email and enquiry form
            come from the database and can be changed without a code deploy.
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
