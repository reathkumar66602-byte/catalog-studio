import { Link, Outlet } from "react-router-dom";
import { useEffect, type ReactNode } from "react";
import { useAuth } from "../../store/auth";
import { useSite } from "./useSite";
import { useI18n } from "../../i18n/LanguageProvider";
import { LanguageSelect } from "../../i18n/LanguageSelect";

export function MarketingLayout() {
  const site = useSite();
  const { user } = useAuth();
  const { t } = useI18n();
  const name = site.branding.siteName;
  const logo = site.branding.logoUrl || "/logo.svg";

  useEffect(() => {
    document.title = t("land.docTitle", { name });
  }, [name, t]);

  return (
    <div className="min-h-screen bg-white text-slate-900">
      <header className="sticky top-0 z-30 border-b border-slate-100 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="flex items-center gap-2 text-lg font-semibold text-sky-800">
            <img src={logo} alt="" className="h-9 w-9 rounded-xl" />
            {name}
          </Link>
          <nav className="flex items-center gap-2 sm:gap-3">
            <LanguageSelect compact />
            <Link to="/contact" className="hidden px-2 py-1.5 text-sm font-medium text-slate-700 hover:text-sky-800 sm:inline">
              {t("marketing.contact")}
            </Link>
            {user && (
              <Link to="/dashboard" className="hidden rounded-lg px-3 py-1.5 text-sm font-semibold text-teal-800 hover:bg-teal-50 sm:inline">
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
        </div>
      </header>
      <Outlet />
      <footer className="border-t border-slate-200 bg-slate-50">
        <div className="mx-auto grid max-w-6xl gap-6 px-4 py-10 md:grid-cols-3">
          <div>
            <p className="font-semibold">{name}</p>
            <p className="mt-2 text-sm text-slate-600">{t("land.footer")}</p>
          </div>
          <div className="text-sm">
            <p className="font-semibold">{t("marketing.support")}</p>
            <a className="mt-2 block text-teal-700" href={`mailto:${site.support.email}`}>
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
