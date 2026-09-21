export function whatsappDigits(raw?: string | null) {
  const cleaned = (raw || "").replace(/\D/g, "");
  return cleaned.startsWith("00") ? cleaned.slice(2) : cleaned;
}

export function formatWhatsapp(raw?: string | null) {
  const digits = whatsappDigits(raw);
  if (!digits) return "";
  if (digits.startsWith("91") && digits.length === 12) {
    return `+91 ${digits.slice(2, 7)} ${digits.slice(7)}`;
  }
  if (digits.length === 10) return `+91 ${digits.slice(0, 5)} ${digits.slice(5)}`;
  return `+${digits}`;
}

export function whatsappHref(raw?: string | null, message?: string) {
  const digits = whatsappDigits(raw);
  const query = message ? `?text=${encodeURIComponent(message)}` : "";
  return digits ? `https://wa.me/${digits}${query}` : `https://wa.me/${query}`;
}
