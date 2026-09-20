import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState, type FormEvent } from "react";
import { api, apiErrorMessage } from "../../api/client";
import { useTheme } from "../../hooks/useTheme";
import { useAuth } from "../../store/auth";
import type { AccountProfile, UserSessionRow, UserSummary } from "../../types";
import { useI18n } from "../../i18n/LanguageProvider";
import { LanguageSelect } from "../../i18n/LanguageSelect";

export function SettingsPage() {
  const { user, updateUser, logout } = useAuth();
  const { t } = useI18n();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { theme, setTheme } = useTheme();
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["me"],
    queryFn: async () => (await api.get("/me")).data.data as AccountProfile,
  });
  const sessions = useQuery({
    queryKey: ["sessions"],
    queryFn: async () => (await api.get("/me/sessions")).data.data as UserSessionRow[],
  });
  const [form, setForm] = useState({
    name: "",
    mobile: "",
    businessName: "",
    gstNumber: "",
    address: "",
    preferredAiProvider: "",
  });
  const [message, setMessage] = useState("");
  const [formError, setFormError] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [passwordMessage, setPasswordMessage] = useState("");

  useEffect(() => {
    if (!data) return;
    setForm({
      name: data.name || "",
      mobile: data.mobile || "",
      businessName: data.businessName || "",
      gstNumber: data.gstNumber || "",
      address: data.address || "",
      preferredAiProvider: data.preferredAiProvider || "",
    });
  }, [data]);

  const saveProfile = useMutation({
    mutationFn: () => api.put("/me", form),
    onSuccess: async (res) => {
      const next = res.data.data as AccountProfile;
      if (user) {
        updateUser({
          ...user,
          name: next.name,
          businessName: next.businessName,
          plan: next.plan ?? user.plan,
          planStatus: next.planStatus ?? user.planStatus,
          accessEntitled: next.accessEntitled ?? user.accessEntitled,
          requiresRecharge: next.requiresRecharge ?? user.requiresRecharge,
        } as UserSummary);
      }
      setMessage(t("settings.saved"));
      setFormError("");
      await qc.invalidateQueries({ queryKey: ["me"] });
    },
    onError: (err) => {
      setMessage("");
      setFormError(apiErrorMessage(err, "Could not save settings"));
    },
  });

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    saveProfile.mutate();
  }

  async function changePassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const f = new FormData(event.currentTarget);
    const currentPassword = String(f.get("currentPassword") || "");
    const newPassword = String(f.get("newPassword") || "");
    const confirmPassword = String(f.get("confirmPassword") || "");
    setPasswordError("");
    setPasswordMessage("");
    if (newPassword !== confirmPassword) {
      setPasswordError("New password and confirm password do not match");
      return;
    }
    try {
      await api.put("/me/password", { currentPassword, newPassword });
      setPasswordMessage("Password updated. Please sign in again.");
      logout();
      navigate("/login");
    } catch (err) {
      setPasswordError(apiErrorMessage(err, "Could not update password"));
    }
  }

  async function revokeOthers() {
    setFormError("");
    setMessage("");
    try {
      await api.post("/auth/logout-others", { refreshToken: localStorage.getItem("cs_refresh") });
      setMessage("Other devices were signed out");
      await qc.invalidateQueries({ queryKey: ["sessions"] });
    } catch (err) {
      setFormError(apiErrorMessage(err, "Could not sign out other devices"));
    }
  }

  if (isLoading) {
    return <p className="text-slate-500">Loading settings...</p>;
  }
  if (isError || !data) {
    return (
      <div className="space-y-3">
        <p className="text-rose-700">{apiErrorMessage(error, "Could not load settings")}</p>
        <button type="button" className="text-sm text-teal-700" onClick={() => refetch()}>
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <h1 className="text-2xl font-semibold">{t("settings.title")}</h1>
      <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-6">
        <h2 className="font-medium">{t("settings.language")}</h2>
        <p className="text-sm text-slate-500">{t("settings.languageHint")}</p>
        <LanguageSelect />
      </section>
      <form onSubmit={save} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6">
        <h2 className="font-medium">{t("settings.account")}</h2>
        {formError && <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{formError}</p>}
        {message && <p className="rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{message}</p>}
        <label className="block text-sm font-medium text-slate-700">
          {t("settings.fullName")}
          <input
            value={form.name}
            onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
            className="mt-1 w-full rounded-xl border px-3 py-2"
            required
          />
        </label>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm font-medium text-slate-700">
            Username
            <input value={data.username || ""} readOnly className="mt-1 w-full rounded-xl border bg-slate-50 px-3 py-2 text-slate-500" />
          </label>
          <label className="block text-sm font-medium text-slate-700">
            Email
            <input value={data.email || ""} readOnly className="mt-1 w-full rounded-xl border bg-slate-50 px-3 py-2 text-slate-500" />
          </label>
        </div>
        <label className="block text-sm font-medium text-slate-700">
          Mobile
          <input
            value={form.mobile}
            onChange={(event) => setForm((current) => ({ ...current, mobile: event.target.value }))}
            className="mt-1 w-full rounded-xl border px-3 py-2"
            placeholder="Mobile"
          />
        </label>
        <h2 className="font-medium">Business</h2>
        <input
          value={form.businessName}
          onChange={(event) => setForm((current) => ({ ...current, businessName: event.target.value }))}
          className="w-full rounded-xl border px-3 py-2"
          placeholder="Business name"
        />
        <input
          value={form.gstNumber}
          onChange={(event) => setForm((current) => ({ ...current, gstNumber: event.target.value }))}
          className="w-full rounded-xl border px-3 py-2"
          placeholder="GST"
        />
        <textarea
          value={form.address}
          onChange={(event) => setForm((current) => ({ ...current, address: event.target.value }))}
          className="w-full rounded-xl border px-3 py-2"
          placeholder="Address"
        />
        <p className="text-xs text-slate-500">
          Plan {data.plan || "—"} · {data.planStatus || "—"}
          {data.emailVerified ? " · email verified" : " · email not verified"}
        </p>
        <h2 className="font-medium">AI settings</h2>
        <select
          value={form.preferredAiProvider}
          onChange={(event) => setForm((current) => ({ ...current, preferredAiProvider: event.target.value }))}
          className="w-full rounded-xl border px-3 py-2"
        >
          <option value="">Workspace default</option>
          <option value="openai">OpenAI</option>
          <option value="gemini">Gemini</option>
          <option value="mock">Local mock (development only)</option>
        </select>
        <Link to="/billing-address" className="block text-sm font-medium text-teal-700">
          Edit structured billing address
        </Link>
        <button disabled={saveProfile.isPending} className="rounded-xl bg-teal-700 px-4 py-2 text-white disabled:opacity-60">
          {saveProfile.isPending ? t("settings.saving") : t("settings.save")}
        </button>
      </form>

      <form onSubmit={changePassword} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6">
        <h2 className="font-medium">Change password</h2>
        {passwordError && <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{passwordError}</p>}
        {passwordMessage && <p className="rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{passwordMessage}</p>}
        <input
          name="currentPassword"
          type="password"
          autoComplete="current-password"
          required
          className="w-full rounded-xl border px-3 py-2"
          placeholder="Current password"
        />
        <input
          name="newPassword"
          type="password"
          autoComplete="new-password"
          minLength={8}
          required
          className="w-full rounded-xl border px-3 py-2"
          placeholder="New password (min 8 characters)"
        />
        <input
          name="confirmPassword"
          type="password"
          autoComplete="new-password"
          minLength={8}
          required
          className="w-full rounded-xl border px-3 py-2"
          placeholder="Confirm new password"
        />
        <button className="rounded-xl bg-teal-700 px-4 py-2 text-white">Update password</button>
      </form>

      <section className="rounded-2xl border border-slate-200 bg-white p-6">
        <div className="flex items-center justify-between gap-3">
          <h2 className="font-medium">Active sessions</h2>
          <button type="button" onClick={revokeOthers} className="text-sm text-teal-700">
            Sign out other devices
          </button>
        </div>
        {sessions.isError && (
          <p className="mt-3 text-sm text-rose-700">{apiErrorMessage(sessions.error, "Could not load sessions")}</p>
        )}
        {sessions.isLoading && <p className="mt-3 text-sm text-slate-500">Loading sessions...</p>}
        <ul className="mt-3 space-y-2 text-sm">
          {(sessions.data || []).map((session) => (
            <li key={session.id} className="rounded-xl border border-slate-100 px-3 py-2">
              <p className="font-medium">{session.deviceName}</p>
              <p className="text-xs text-slate-500">
                {session.ipAddress || "IP hidden"}
                {session.lastUsedAt ? ` · last used ${new Date(session.lastUsedAt).toLocaleString("en-IN")}` : ""}
              </p>
            </li>
          ))}
        </ul>
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-6">
        <h2 className="font-medium">Appearance</h2>
        <div className="mt-3 flex gap-3">
          <button
            type="button"
            onClick={() => setTheme("light")}
            className={`rounded-xl border px-4 py-2 ${theme === "light" ? "border-teal-700" : ""}`}
          >
            Light
          </button>
          <button
            type="button"
            onClick={() => setTheme("dark")}
            className={`rounded-xl border px-4 py-2 ${theme === "dark" ? "border-teal-700" : ""}`}
          >
            Dark
          </button>
        </div>
      </section>
    </div>
  );
}
