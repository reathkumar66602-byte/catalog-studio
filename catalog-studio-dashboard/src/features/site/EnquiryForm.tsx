import { useState, type FormEvent } from "react";
import { api, apiErrorMessage } from "../../api/client";
import { useSite } from "./useSite";
import { useI18n } from "../../i18n/LanguageProvider";

type EnquiryFields = {
  name: string;
  email: string;
  phone: string;
  storeName: string;
  subject: string;
  message: string;
};

type EnquiryErrors = Partial<Record<keyof EnquiryFields, string>>;
type Translate = (key: string, vars?: Record<string, string | number>) => string;

const EMPTY: EnquiryFields = {
  name: "",
  email: "",
  phone: "",
  storeName: "",
  subject: "",
  message: "",
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const NAME_PATTERN = /^[\p{L}][\p{L} .'-]{1,119}$/u;
const PHONE_PATTERN = /^(\+91[\s-]?|91[\s-]?|0)?[6-9]\d{9}$/;

export function EnquiryForm({ id = "enquiry" }: { id?: string }) {
  const site = useSite();
  const { t } = useI18n();
  const [status, setStatus] = useState<"idle" | "saving" | "ok" | "error">("idle");
  const [message, setMessage] = useState("");
  const [values, setValues] = useState<EnquiryFields>({
    ...EMPTY,
    storeName: site.client?.storeName || "",
  });
  const [errors, setErrors] = useState<EnquiryErrors>({});

  if (!site.enquiry.enabled) {
    return (
      <div id={id} className="rounded-3xl border border-slate-200 bg-white p-6">
        <h2 className="text-xl font-semibold">{t("enq.paused")}</h2>
        <p className="mt-2 text-sm text-slate-600">
          {t("enq.emailInstead", { email: site.support.email })}
        </p>
      </div>
    );
  }

  function setField<K extends keyof EnquiryFields>(key: K, value: EnquiryFields[K]) {
    setValues((current) => ({ ...current, [key]: value }));
    if (errors[key]) {
      setErrors((current) => {
        const next = { ...current };
        delete next[key];
        return next;
      });
    }
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors = validateEnquiry(values, t);
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      setStatus("error");
      setMessage(t("enq.fix"));
      return;
    }
    setStatus("saving");
    setMessage("");
    try {
      const { data } = await api.post("/site/enquiries", {
        name: values.name.trim(),
        email: values.email.trim(),
        phone: values.phone.trim() || undefined,
        storeName: values.storeName.trim() || undefined,
        subject: values.subject.trim(),
        message: values.message.trim(),
      });
      setStatus("ok");
      setMessage(data.data?.message || site.enquiry.successMessage || t("enq.thanks"));
      setValues({ ...EMPTY, storeName: site.client?.storeName || "" });
      setErrors({});
    } catch (err) {
      const fieldErrors = apiFieldErrors(err);
      if (Object.keys(fieldErrors).length > 0) {
        setErrors(fieldErrors);
        setStatus("error");
        setMessage(t("enq.fix"));
        return;
      }
      setStatus("error");
      setMessage(apiErrorMessage(err, t("enq.fail")));
    }
  }

  return (
    <form
      id={id}
      noValidate
      onSubmit={onSubmit}
      className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm md:p-8"
    >
      <h2 className="text-xl font-semibold">{t("enq.title")}</h2>
      <p className="mt-2 text-sm text-slate-600">{t("enq.intro")}</p>
      <p className="mt-1 text-sm text-slate-500">
        {t("enq.supportEmail")}{" "}
        <a className="font-medium text-teal-700" href={`mailto:${site.support.email}`}>
          {site.support.email}
        </a>
      </p>
      <div className="mt-5 grid gap-3 sm:grid-cols-2">
        <Field
          label={t("enq.name")}
          name="name"
          autoComplete="name"
          required
          value={values.name}
          error={errors.name}
          placeholder={t("enq.phName")}
          onChange={(value) => setField("name", value)}
        />
        <Field
          label={t("enq.email")}
          name="email"
          type="email"
          autoComplete="email"
          required
          value={values.email}
          error={errors.email}
          placeholder={t("enq.phEmail")}
          onChange={(value) => setField("email", value)}
        />
        <Field
          label={t("enq.phone")}
          name="phone"
          type="tel"
          autoComplete="tel"
          hint={t("enq.phoneHint")}
          value={values.phone}
          error={errors.phone}
          placeholder={t("enq.phPhone")}
          onChange={(value) => setField("phone", value)}
        />
        <Field
          label={t("enq.store")}
          name="storeName"
          autoComplete="organization"
          value={values.storeName}
          error={errors.storeName}
          placeholder={t("enq.phStore")}
          onChange={(value) => setField("storeName", value)}
        />
      </div>
      <Field
        className="mt-3"
        label={t("enq.subject")}
        name="subject"
        required
        value={values.subject}
        error={errors.subject}
        placeholder={t("enq.phSubject")}
        onChange={(value) => setField("subject", value)}
      />
      <Field
        className="mt-3"
        label={t("enq.message")}
        name="message"
        required
        multiline
        rows={5}
        hint={t("enq.messageHint")}
        value={values.message}
        error={errors.message}
        placeholder={t("enq.phMessage")}
        onChange={(value) => setField("message", value)}
      />
      <button
        type="submit"
        disabled={status === "saving"}
        className="mt-4 rounded-xl bg-teal-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-800 disabled:opacity-60"
      >
        {status === "saving" ? t("enq.sending") : t("enq.send")}
      </button>
      {message && (
        <p className={`mt-3 text-sm ${status === "error" ? "text-red-600" : "text-teal-800"}`}>{message}</p>
      )}
    </form>
  );
}

function Field({
  label,
  name,
  value,
  onChange,
  error,
  required,
  type = "text",
  autoComplete,
  hint,
  placeholder,
  multiline,
  rows = 4,
  className = "",
}: {
  label: string;
  name: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  required?: boolean;
  type?: string;
  autoComplete?: string;
  hint?: string;
  placeholder?: string;
  multiline?: boolean;
  rows?: number;
  className?: string;
}) {
  const inputClass = `mt-1 w-full rounded-xl border px-3 py-2.5 ${
    error ? "border-red-400 bg-red-50" : "border-slate-200"
  }`;
  return (
    <label className={`block text-sm font-medium ${className}`}>
      {label}
      {required ? <span className="text-red-600"> *</span> : null}
      {multiline ? (
        <textarea
          name={name}
          rows={rows}
          value={value}
          placeholder={placeholder}
          aria-invalid={Boolean(error)}
          className={inputClass}
          onChange={(event) => onChange(event.target.value)}
        />
      ) : (
        <input
          name={name}
          type={type}
          autoComplete={autoComplete}
          value={value}
          placeholder={placeholder}
          aria-invalid={Boolean(error)}
          className={inputClass}
          onChange={(event) => onChange(event.target.value)}
        />
      )}
      {error ? <span className="mt-1 block text-xs font-normal text-red-600">{error}</span> : null}
      {!error && hint ? <span className="mt-1 block text-xs font-normal text-slate-500">{hint}</span> : null}
    </label>
  );
}

function validateEnquiry(values: EnquiryFields, t: Translate): EnquiryErrors {
  const errors: EnquiryErrors = {};
  const name = values.name.trim();
  const email = values.email.trim();
  const phone = values.phone.trim();
  const storeName = values.storeName.trim();
  const subject = values.subject.trim();
  const message = values.message.trim();

  if (!name) errors.name = t("enq.errName");
  else if (name.length < 2 || name.length > 120) errors.name = t("enq.errNameLen");
  else if (!NAME_PATTERN.test(name)) errors.name = t("enq.errNameChars");

  if (!email) errors.email = t("enq.errEmail");
  else if (!EMAIL_PATTERN.test(email) || email.length > 255) errors.email = t("enq.errEmailValid");

  if (phone && !PHONE_PATTERN.test(phone)) errors.phone = t("enq.errPhone");

  if (storeName.length > 200) errors.storeName = t("enq.errStore");

  if (!subject) errors.subject = t("enq.errSubject");
  else if (subject.length < 5 || subject.length > 200) errors.subject = t("enq.errSubjectLen");

  if (!message) errors.message = t("enq.errMsg");
  else if (message.length < 20 || message.length > 4000) errors.message = t("enq.errMsgLen");

  return errors;
}

function apiFieldErrors(error: unknown): EnquiryErrors {
  if (!isRecord(error) || !isRecord(error.response) || !isRecord(error.response.data)) return {};
  const errors = error.response.data.errors;
  if (!Array.isArray(errors)) return {};
  const mapped: EnquiryErrors = {};
  for (const item of errors) {
    if (!isRecord(item) || typeof item.field !== "string" || typeof item.message !== "string") continue;
    if (item.field in EMPTY) {
      mapped[item.field as keyof EnquiryFields] = item.message;
    }
  }
  return mapped;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
