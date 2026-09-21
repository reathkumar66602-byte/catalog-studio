import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import type { ApiResponse } from "../../types";
import { useI18n } from "../../i18n/LanguageProvider";
import { AccountShell, Field } from "./LoginPage";

export function ForgotPasswordPage() {
  const { t } = useI18n();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const email = String(form.get("email") || "").trim();
    setLoading(true);
    setError("");
    try {
      await api.post<ApiResponse<void>>("/auth/forgot-password", { email });
      setSent(true);
    } catch (err) {
      setError(apiErrorMessage(err, t("forgot.error")));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AccountShell>
      <h2 className="text-xl font-semibold text-slate-900">{t("forgot.title")}</h2>
      <p className="mt-2 text-sm text-slate-500">{t("forgot.subtitle")}</p>
      {sent ? (
        <p className="mt-6 rounded-lg bg-teal-50 px-3 py-2 text-sm text-teal-900">{t("forgot.sent")}</p>
      ) : (
        <form onSubmit={onSubmit} className="mt-6 space-y-5">
          {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
          <Field label={t("register.email")} name="email" type="email" required autoComplete="email" />
          <button
            disabled={loading}
            className="rounded-md bg-teal-800 px-5 py-2.5 text-sm font-medium text-white hover:bg-teal-900 disabled:opacity-60"
          >
            {loading ? t("forgot.sending") : t("forgot.submit")}
          </button>
        </form>
      )}
      <div className="mt-8 border-t border-slate-200 bg-slate-50 px-6 py-3 text-sm text-slate-600 -mx-6 -mb-6 rounded-b-lg">
        <Link to="/login" className="font-medium text-teal-800">
          {t("forgot.back")}
        </Link>
      </div>
    </AccountShell>
  );
}
