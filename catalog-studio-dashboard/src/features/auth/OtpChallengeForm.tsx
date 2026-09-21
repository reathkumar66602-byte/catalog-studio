import { useState, type FormEvent } from "react";
import { api, apiErrorMessage } from "../../api/client";
import type { ApiResponse, AuthFlow } from "../../types";
import { useI18n } from "../../i18n/LanguageProvider";

export function OtpChallengeForm({
  challenge,
  onVerified,
  onBack,
}: {
  challenge: AuthFlow;
  onVerified: (flow: AuthFlow) => void;
  onBack: () => void;
}) {
  const { t } = useI18n();
  const [otp, setOtp] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [info, setInfo] = useState(t("otp.sent", { email: challenge.maskedEmail || t("enq.email") }));

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      const { data } = await api.post<ApiResponse<AuthFlow>>("/auth/otp/verify", {
        challengeId: challenge.challengeId,
        otp,
      });
      onVerified(data.data);
    } catch (err) {
      setError(apiErrorMessage(err, t("otp.verify")));
    } finally {
      setLoading(false);
    }
  }

  async function resend() {
    setError("");
    try {
      const { data } = await api.post<ApiResponse<{ maskedEmail?: string }>>("/auth/otp/resend", {
        challengeId: challenge.challengeId,
      });
      setInfo(t("otp.sent", { email: data.data.maskedEmail || challenge.maskedEmail || "" }));
    } catch (err) {
      setError(apiErrorMessage(err, t("enq.fail")));
    }
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <h2 className="text-xl font-semibold text-slate-900">{t("otp.title")}</h2>
      <p className="text-sm text-slate-600">{info}</p>
      {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      <label className="block text-sm font-medium text-slate-800">
        {t("otp.label")} <span className="text-rose-700">*</span>
        <input
          value={otp}
          onChange={(event) => setOtp(event.target.value.replace(/\D/g, "").slice(0, 8))}
          inputMode="numeric"
          autoComplete="one-time-code"
          required
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2.5 tracking-[0.4em] outline-none focus:border-teal-700"
        />
      </label>
      <div className="flex flex-wrap gap-3">
        <button
          disabled={loading || otp.length < 4}
          className="rounded-md bg-teal-800 px-5 py-2.5 text-sm font-medium text-white hover:bg-teal-900 disabled:opacity-60"
        >
          {loading ? t("otp.verifying") : t("otp.verify")}
        </button>
        <button type="button" onClick={resend} className="rounded-md border border-slate-300 px-4 py-2.5 text-sm">
          {t("otp.resend")}
        </button>
        <button type="button" onClick={onBack} className="px-2 py-2.5 text-sm text-slate-500">
          {t("otp.back")}
        </button>
      </div>
    </form>
  );
}
