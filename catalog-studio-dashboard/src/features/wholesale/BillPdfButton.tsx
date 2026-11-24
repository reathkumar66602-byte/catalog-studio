import { useState } from "react";
import { downloadBillPdf } from "./wholesale";

type Props = {
  url: string;
  orderNumber?: string | null;
  className?: string;
  children?: React.ReactNode;
};

export function BillPdfButton({ url, orderNumber, className, children }: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const onClick = async () => {
    setError("");
    setBusy(true);
    try {
      await downloadBillPdf(url, orderNumber);
    } catch {
      setError("Download failed. Try again.");
      // Fallback: open the raw file URL (works after service-worker denylist deploy).
      window.open(url, "_blank", "noopener,noreferrer");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="space-y-1">
      <button type="button" disabled={busy} onClick={onClick} className={className}>
        {busy ? "Downloading…" : children || "Download bill PDF"}
      </button>
      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  );
}
