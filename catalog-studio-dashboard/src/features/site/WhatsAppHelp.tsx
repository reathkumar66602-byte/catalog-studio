import { useState } from "react";
import { Check, Copy, Smartphone } from "lucide-react";
import { useI18n } from "../../i18n/LanguageProvider";
import { useSite } from "./useSite";
import { formatWhatsapp, whatsappDigits, whatsappHref } from "./whatsapp";

export function WhatsAppHelp() {
  const site = useSite();
  const { t } = useI18n();
  const [copied, setCopied] = useState(false);
  const digits = whatsappDigits(site.support.whatsapp);
  if (!digits) return null;
  const display = formatWhatsapp(digits);

  async function copyNumber() {
    try {
      await navigator.clipboard.writeText(display || digits);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      setCopied(false);
    }
  }

  return (
    <div className="mt-6 rounded-xl border border-teal-200 bg-teal-50 px-4 py-3 text-sm text-teal-950">
      <p>{t("login.waHelp")}</p>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <code className="rounded-lg bg-white px-3 py-1.5 text-sm font-semibold text-teal-800">{display}</code>
        <button
          type="button"
          onClick={copyNumber}
          className="inline-flex items-center gap-1 rounded-lg border border-teal-300 bg-white px-2.5 py-1.5 text-xs font-medium"
        >
          {copied ? <Check size={14} /> : <Copy size={14} />}
          {copied ? t("common.copied") : t("login.waCopy")}
        </button>
        <a
          href={whatsappHref(digits, t("login.waMessage"))}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-1 rounded-lg bg-[#128C7E] px-2.5 py-1.5 text-xs font-medium text-white"
        >
          <Smartphone size={14} />
          {t("login.waOpen")}
        </a>
      </div>
    </div>
  );
}
