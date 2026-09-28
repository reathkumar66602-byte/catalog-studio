import { Link, Outlet, useLocation } from "react-router-dom";
import { useEffect, useState, type ReactNode } from "react";
import { Menu, X } from "lucide-react";
import { useAuth } from "../../store/auth";
import { useSite } from "./useSite";
import { useI18n } from "../../i18n/LanguageProvider";
import { LanguageSelect } from "../../i18n/LanguageSelect";
import { ParentCompanyMark } from "../../components/ParentCompanyMark";

export function MarketingLayout() {
  const site = useSite();
  const { user } = useAuth();
  const { t } = useI18n();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const name = site.branding.siteName;
  const logo = site.branding.logoUrl || "/logo.svg";

  useEffect(() => {
    document.title = t("land.docTitle", { name });
  }, [name, t]);

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

  return (
    <div className="min-h-dvh overflow-x-clip bg-white text-slate-900">
      <header className="sticky top-0 z-30 border-b border-slate-100 bg-white pt-[env(safe-area-inset-top)]">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-2 px-3 py-3 sm:gap-4 sm:px-4">
          <Link to="/" className="flex min-w-0 flex-col gap-0.5">
            <span className="flex min-w-0 items-center gap-2 text-base font-semibold text-sky-800 sm:text-lg">
              <img src={logo} alt="" className="h-8 w-8 shrink-0 rounded-xl sm:h-9 sm:w-9" />
              <span className="truncate">{name}</span>
            </span>
            <ParentCompanyMark variant="inline" className="hidden pl-10 sm:flex" />
          </Link>
          <div className="flex shrink-0 items-center gap-1.5 sm:gap-3">
            <LanguageSelect compact />
            <nav className="hidden items-center gap-2 sm:flex sm:gap-3">
              <Link to="/contact" className="px-2 py-1.5 text-sm font-medium text-slate-700 hover:text-sky-800">
                {t("marketing.contact")}
              </Link>
              {user && (
                <Link to="/dashboard" className="rounded-lg px-3 py-1.5 text-sm font-semibold text-teal-800 hover:bg-teal-50">
                  {t("marketing.workspace")}
                </Link>
              )}
              <Link to="/login" className="rounded-lg px-3 py-1.5 text-sm font-semibold text-slate-800 hover:bg-slate-50">
                {t("login.submit")}
              </Link>
              <Link to="/register" className="rounded-lg bg-teal-700 px-3 py-1.5 text-sm font-semibold text-white hover:bg-teal-800">
                {t("register.submit")}
              </Link>
            </nav>
            <button
              type="button"
              className="rounded-lg p-2 text-slate-700 hover:bg-slate-100 sm:hidden"
              aria-label={mobileOpen ? "Close menu" : "Open menu"}
              aria-expanded={mobileOpen}
              onClick={() => setMobileOpen((value) => !value)}
            >
              {mobileOpen ? <X size={20} /> : <Menu size={20} />}
            </button>
          </div>
        </div>
      </header>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 sm:hidden" role="dialog" aria-modal="true" aria-label="Site menu">
          <button
            type="button"
            className="absolute inset-0 bg-slate-950/40"
            aria-label="Close menu"
            onClick={() => setMobileOpen(false)}
          />
          <div className="absolute inset-x-0 top-[calc(env(safe-area-inset-top)+3.75rem)] mx-3 rounded-2xl border border-slate-200 bg-white p-3 shadow-xl">
            <nav className="flex flex-col gap-1">
              <Link to="/contact" className="rounded-xl px-3 py-3 text-sm font-medium text-slate-800 hover:bg-slate-50">
                {t("marketing.contact")}
              </Link>
              {user && (
                <Link to="/dashboard" className="rounded-xl px-3 py-3 text-sm font-semibold text-teal-800 hover:bg-teal-50">
                  {t("marketing.workspace")}
                </Link>
              )}
              <Link to="/privacy" className="rounded-xl px-3 py-3 text-sm font-medium text-slate-800 hover:bg-slate-50">
                {t("marketing.privacy")}
              </Link>
              <Link to="/login" className="rounded-xl px-3 py-3 text-sm font-semibold text-slate-800 hover:bg-slate-50">
                {t("login.submit")}
              </Link>
              <Link to="/register" className="rounded-xl bg-teal-700 px-3 py-3 text-center text-sm font-semibold text-white hover:bg-teal-800">
                {t("register.submit")}
              </Link>
            </nav>
          </div>
        </div>
      )}

      <Outlet />
      <footer className="border-t border-slate-200 bg-slate-50 pb-[env(safe-area-inset-bottom)]">
        <div className="mx-auto grid max-w-6xl gap-6 px-4 py-10 md:grid-cols-3">
          <div>
            <p className="font-semibold">{name}</p>
            <p className="mt-2 text-sm text-slate-600">{t("land.footer")}</p>
            <ParentCompanyMark variant="footer" productName={name} className="mt-3" />
          </div>
          <div className="text-sm">
            <p className="font-semibold">{t("marketing.support")}</p>
            <a className="mt-2 block break-all text-teal-700" href={`mailto:${site.support.email}`}>
              {site.support.email}
            </a>
            {site.support.phone && <p className="mt-1 text-slate-600">{site.support.phone}</p>}
            {site.client && <p className="mt-3 text-slate-600">{t("land.featuredStore", { name: site.client.storeName })}</p>}
          </div>
          <div className="text-sm">
            <p className="font-semibold">{t("marketing.account")}</p>
            <div className="mt-2 flex flex-col gap-1">
              <Link to="/contact" className="text-slate-600 hover:text-sky-800">
                {t("marketing.contact")}
              </Link>
              <Link to="/privacy" className="text-slate-600 hover:text-sky-800">
                {t("marketing.privacy")}
              </Link>
              <Link to="/login" className="text-slate-600 hover:text-sky-800">
                {t("login.submit")}
              </Link>
              <Link to="/register" className="text-slate-600 hover:text-sky-800">
                {t("register.submit")}
              </Link>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}

export function PublicToolShell({ children }: { children: ReactNode }) {
  return <div className="bg-slate-50 px-4 py-10">{children}</div>;
}
