import { useState, type FormEvent, type InputHTMLAttributes, type ReactNode } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Eye, EyeOff } from "lucide-react";
import { api, apiErrorMessage } from "../../api/client";
import type { ApiResponse, AuthFlow } from "../../types";
import { useAuth } from "../../store/auth";
import { useI18n } from "../../i18n/LanguageProvider";
import { LanguageSelect } from "../../i18n/LanguageSelect";

export function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { setSession } = useAuth();
  const { t } = useI18n();
  const [show, setShow] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function enterSession(flow: AuthFlow) {
    if (!flow.session) {
      setError("Login did not return a session");
      return;
    }
    setSession(flow.session.user, flow.session.accessToken, flow.session.refreshToken);
    const requested = params.get("next");
    const next = flow.session.user.requiresRecharge
      ? "/subscription"
      : requested && requested.startsWith("/") && !requested.startsWith("//")
        ? requested
        : "/dashboard";
    navigate(next);
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setLoading(true);
    setError("");
    try {
      const { data } = await api.post<ApiResponse<AuthFlow>>("/auth/login", {
        login: form.get("login"),
        password: form.get("password"),
        rememberDevice: form.get("remember") === "on",
        deviceName: navigator.userAgent.slice(0, 80),
      });
      enterSession(data.data);
    } catch (err) {
      setError(apiErrorMessage(err, "Invalid email or password"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AccountShell>
      <h2 className="text-xl font-semibold text-slate-900">{t("login.title")}</h2>
      <form onSubmit={onSubmit} className="mt-6 space-y-5">
        {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
        <Field label={t("login.email")} name="login" required autoComplete="username" />
        <label className="block text-sm font-medium text-slate-800">
          {t("login.password")} <span className="text-rose-700">*</span>
          <div className="relative mt-1">
            <input
              name="password"
              type={show ? "text" : "password"}
              required
              autoComplete="current-password"
              className="w-full rounded-md border border-slate-300 px-3 py-2.5 pr-40 outline-none focus:border-teal-700"
            />
            <button
              type="button"
              className="absolute right-3 top-2.5 inline-flex items-center gap-1 text-sm text-teal-800"
              onClick={() => setShow(!show)}
            >
              {show ? <EyeOff size={16} /> : <Eye size={16} />}
              {show ? t("login.hide") : t("login.show")}
            </button>
          </div>
        </label>
        <div className="flex justify-end -mt-2">
          <Link to="/forgot-password" className="text-sm font-medium text-teal-800">
            {t("login.forgot")}
          </Link>
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input type="checkbox" name="remember" className="accent-teal-700" />
          {t("login.remember")}
        </label>
        <button
          disabled={loading}
          className="rounded-md bg-teal-800 px-5 py-2.5 text-sm font-medium text-white hover:bg-teal-900 disabled:opacity-60"
        >
          {loading ? t("login.signing") : t("login.submit")}
        </button>
      </form>
      <div className="mt-8 border-t border-slate-200 bg-slate-50 px-6 py-3 text-sm text-slate-600 -mx-6 -mb-6 rounded-b-lg">
        {t("login.newSeller")}{" "}
        <Link to="/register" className="font-medium text-teal-800">
          {t("login.create")}
        </Link>
      </div>
    </AccountShell>
  );
}

export function Field({ label, required, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <label className="block text-sm font-medium text-slate-800">
      {label} {required && <span className="text-rose-700">*</span>}
      <input
        {...props}
        required={required}
        className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2.5 outline-none focus:border-teal-700"
      />
    </label>
  );
}

export function AccountShell({ children }: { children: ReactNode }) {
  return (
    <div className="relative flex min-h-screen items-start justify-center bg-[#f7f7f7] px-4 py-10 md:py-16">
      <div className="absolute right-4 top-4">
        <LanguageSelect compact />
      </div>
      <div className="w-full max-w-3xl rounded-lg border border-slate-200 bg-white p-6 shadow-sm md:p-8">
        {children}
      </div>
    </div>
  );
}

export function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <AccountShell>
      <h2 className="text-xl font-semibold text-slate-900">{title}</h2>
      <p className="mt-2 text-sm text-slate-500">{subtitle}</p>
      <div className="mt-6">{children}</div>
    </AccountShell>
  );
}
