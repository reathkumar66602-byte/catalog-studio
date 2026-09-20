import { useEffect, useState, type ReactNode } from "react";
import type { LabelMarketplace, PrinterMode } from "./pdf/cropLabels";
import { cropShippingLabels } from "./pdf/cropLabels";
import { cropDownloadName, downloadPdf } from "./pdf/download";
import { FileRow, ToolCard } from "./LabelToolUi";

const MAX_BYTES = 40 * 1024 * 1024;

type Props = {
  marketplace: LabelMarketplace;
  title: string;
  description: string;
  logo: ReactNode;
  extraLinks: ReactNode;
  defaultPrinter?: PrinterMode;
};

export function LabelCropPage({ marketplace, title, description, logo, extraLinks, defaultPrinter = "label" }: Props) {
  const [file, setFile] = useState<File | null>(null);
  const [printer, setPrinter] = useState<PrinterMode>(defaultPrinter);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [prepared, setPrepared] = useState<Uint8Array | null>(null);
  const [savedName, setSavedName] = useState("");

  function onChoose(list: FileList | null) {
    const next = list?.[0];
    if (!next) return;
    if (next.type !== "application/pdf" && !next.name.toLowerCase().endsWith(".pdf")) {
      setError("Choose a PDF file");
      setFile(null);
      return;
    }
    if (next.size > MAX_BYTES) {
      setError("PDF must be 40MB or smaller");
      setFile(null);
      return;
    }
    setError("");
    setSavedName("");
    setFile(next);
  }

  useEffect(() => {
    if (!file) {
      setPrepared(null);
      return;
    }
    let cancelled = false;
    setBusy(true);
    setError("");
    setPrepared(null);
    void (async () => {
      try {
        const bytes = await cropShippingLabels(await file.arrayBuffer(), { marketplace, printer });
        if (!cancelled) setPrepared(bytes);
      } catch (err) {
        if (!cancelled) {
          setPrepared(null);
          setError(err instanceof Error ? err.message : "Could not crop this PDF");
        }
      } finally {
        if (!cancelled) setBusy(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [file, marketplace, printer]);

  function save(bytes: Uint8Array) {
    const name = cropDownloadName();
    downloadPdf(bytes, name);
    setSavedName(name);
  }

  function prepare() {
    if (!prepared) {
      setError("Choose a PDF file");
      return;
    }
    save(prepared);
  }

  return (
    <ToolCard title={title}>
      <p className="mt-3 text-sm leading-relaxed text-slate-600 dark:text-slate-400">{description}</p>
      <p className="mt-4 text-sm text-slate-600 dark:text-slate-400">
        Simply upload a PDF file to use the auto-all label crop tool.
      </p>
      <FileRow logo={logo}>
        <input
          type="file"
          accept="application/pdf,.pdf"
          className="w-full text-sm file:mr-3 file:rounded-lg file:border file:border-slate-300 file:bg-white file:px-3 file:py-1.5"
          onChange={(event) => onChoose(event.target.files)}
        />
      </FileRow>
      <div className="mt-4 flex flex-wrap items-center gap-5 text-sm">
        <label className="flex items-center gap-2">
          <input
            type="radio"
            name={`${marketplace}-printer`}
            checked={printer === "label"}
            onChange={() => setPrinter("label")}
          />
          Label Printer
        </label>
        <label className="flex items-center gap-2">
          <input
            type="radio"
            name={`${marketplace}-printer`}
            checked={printer === "a4"}
            onChange={() => setPrinter("a4")}
          />
          A4 Printer
        </label>
      </div>
      <button
        type="button"
        onClick={prepare}
        disabled={busy || !prepared}
        className="mt-5 rounded-xl bg-sky-800 px-5 py-2.5 text-sm font-semibold text-white hover:bg-sky-900 disabled:opacity-50"
      >
        {busy ? "Preparing..." : "Prepare Shipping Labels"}
      </button>
      {savedName && (
        <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
          Saved to Downloads as {savedName}.{" "}
          {prepared && (
            <button type="button" className="font-semibold text-sky-800 underline" onClick={() => save(prepared)}>
              Download again
            </button>
          )}
        </p>
      )}
      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
      <div className="mt-6 flex flex-wrap gap-3">{extraLinks}</div>
    </ToolCard>
  );
}
