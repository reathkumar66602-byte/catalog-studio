import { useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Eye, EyeOff } from "lucide-react";
import { api, apiErrorMessage } from "../../api/client";
import type { ApiResponse } from "../../types";
import { useI18n } from "../../i18n/LanguageProvider";
import { AccountShell } from "./LoginPage";

export function ResetPasswordPage() {
  const { t } = useI18n();
  const [params] = useSearchParams();
  const token = (params.get("token") || "").trim();
  const [show, setShow] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const password = String(form.get("password") || "");
    const confirmPassword = String(form.get("confirmPassword") || "");
    if (password !== confirmPassword) {
      setError(t("reset.mismatch"));
      return;
    }
    setLoading(true);
    setError("");
    try {
      await api.post<ApiResponse<void>>("/auth/reset-password", { token, password, confirmPassword });
      setDone(true);
    } catch (err) {
      setError(apiErrorMessage(err, t("reset.invalid")));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AccountShell>
      <h2 className="text-xl font-semibold text-slate-900">{t("reset.title")}</h2>
      <p className="mt-2 text-sm text-slate-500">{t("reset.subtitle")}</p>
      {!token ? (
        <p className="mt-6 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{t("reset.invalid")}</p>
      ) : done ? (
        <p className="mt-6 rounded-lg bg-teal-50 px-3 py-2 text-sm text-teal-900">{t("reset.success")}</p>
      ) : (
        <form onSubmit={onSubmit} className="mt-6 space-y-5">
          {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
          <label className="block text-sm font-medium text-slate-800">
            {t("reset.newPassword")} <span className="text-rose-700">*</span>
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
          <label className="block text-sm font-medium text-slate-800">
            {t("reset.confirmPassword")} <span className="text-rose-700">*</span>
            <input
              name="confirmPassword"
              type={show ? "text" : "password"}
              required
              minLength={8}
              autoComplete="new-password"
              className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2.5 outline-none focus:border-teal-700"
            />
          </label>
          <button
            disabled={loading}
            className="rounded-md bg-teal-800 px-5 py-2.5 text-sm font-medium text-white hover:bg-teal-900 disabled:opacity-60"
          >
            {loading ? t("reset.updating") : t("reset.submit")}
          </button>
        </form>
      )}
      <div className="mt-8 border-t border-slate-200 bg-slate-50 px-6 py-3 text-sm text-slate-600 -mx-6 -mb-6 rounded-b-lg">
        <Link to="/login" className="font-medium text-teal-800">
          {t("reset.signIn")}
        </Link>
      </div>
    </AccountShell>
  );
}
