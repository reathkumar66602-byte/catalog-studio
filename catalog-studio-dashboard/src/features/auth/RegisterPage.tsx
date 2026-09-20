import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Eye, EyeOff } from "lucide-react";
import { api, apiErrorMessage } from "../../api/client";
import type { ApiResponse, AuthFlow } from "../../types";
import { useAuth } from "../../store/auth";
import { AccountShell, Field } from "./LoginPage";
import { OtpChallengeForm } from "./OtpChallengeForm";
import { useI18n } from "../../i18n/LanguageProvider";

export function RegisterPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { setSession } = useAuth();
  const { t } = useI18n();
  const [show, setShow] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [otp, setOtp] = useState<AuthFlow | null>(null);
  const referral = params.get("ref") || "";

  function finish(flow: AuthFlow) {
    if (flow.otpRequired && flow.challengeId) {
      setOtp(flow);
      return;
    }
    if (!flow.session) {
      setError("Registration did not return a session");
      return;
    }
    setSession(flow.session.user, flow.session.accessToken, flow.session.refreshToken);
    navigate(flow.session.user.requiresRecharge ? "/subscription" : "/dashboard");
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const password = String(form.get("password") || "");
    const confirmPassword = String(form.get("confirmPassword") || "");
    const email = String(form.get("email") || "");
    const confirmEmail = String(form.get("confirmEmail") || "");
    if (password !== confirmPassword) {
      setError(t("register.passwordMismatch"));
      return;
    }
    if (email.trim().toLowerCase() !== confirmEmail.trim().toLowerCase()) {
      setError(t("register.emailMismatch"));
      return;
    }
    setLoading(true);
    setError("");
    try {
      const { data } = await api.post<ApiResponse<AuthFlow>>("/auth/register", {
        username: form.get("username"),
        email,
        confirmEmail,
        password,
        confirmPassword,
        referralCode: referral || undefined,
        termsAccepted: true,
      });
      finish(data.data);
    } catch (err) {
      setError(apiErrorMessage(err, "Could not create account"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AccountShell>
      {otp ? (
        <OtpChallengeForm challenge={otp} onVerified={finish} onBack={() => setOtp(null)} />
      ) : (
        <>
          <h2 className="text-xl font-semibold text-slate-900">{t("login.title")}</h2>
          <form onSubmit={onSubmit} className="mt-6 space-y-5">
            {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
            <Field label={t("register.username")} name="username" required autoComplete="username" minLength={3} />
            <div className="grid gap-4 md:grid-cols-2">
              <label className="block text-sm font-medium text-slate-800">
                {t("login.password")} <span className="text-rose-700">*</span>
                <div className="relative mt-1">
                  <input
                    name="password"
                    type={show ? "text" : "password"}
                    required
                    minLength={8}
                    autoComplete="new-password"
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
              <Field label={t("register.confirmPassword")} name="confirmPassword" type="password" required autoComplete="new-password" />
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              <Field label={t("register.email")} name="email" type="email" required autoComplete="email" />
              <Field label={t("register.confirmEmail")} name="confirmEmail" type="email" required autoComplete="email" />
            </div>
            <button
              disabled={loading}
              className="rounded-md bg-teal-800 px-5 py-2.5 text-sm font-medium text-white hover:bg-teal-900 disabled:opacity-60"
            >
              {loading ? t("register.creating") : t("register.submit")}
            </button>
          </form>
        </>
      )}
      <div className="mt-8 border-t border-slate-200 bg-slate-50 px-6 py-3 text-sm text-slate-600 -mx-6 -mb-6 rounded-b-lg">
        {t("register.haveAccount")}{" "}
        <Link to="/login" className="font-medium text-teal-800">
          {t("register.signIn")}
        </Link>
      </div>
    </AccountShell>
  );
}
