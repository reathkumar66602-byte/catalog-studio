import { useEffect, useState } from "react";
import { MergePdfLogo } from "./logos";
import { FileRow, FlipkartToolLink, MeeshoToolLink, ToolCard } from "./LabelToolUi";
import { downloadPdf, mergeDownloadName } from "./pdf/download";
import { mergePdfs } from "./pdf/mergePdfs";

const MAX_FILES = 40;
const MAX_BYTES = 25 * 1024 * 1024;

export function MergePdfPage() {
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [prepared, setPrepared] = useState<Uint8Array | null>(null);
  const [savedName, setSavedName] = useState("");

  function onChoose(list: FileList | null) {
    if (!list) return;
    const next = Array.from(list).filter((file) => file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf"));
    if (next.length === 0) {
      setError("Choose PDF files only");
      return;
    }
    if (next.length > MAX_FILES) {
      setError(`Choose at most ${MAX_FILES} PDF files`);
      return;
    }
    if (next.some((file) => file.size > MAX_BYTES)) {
      setError("Each PDF must be 25MB or smaller");
      return;
    }
    setError("");
    setSavedName("");
    setFiles(next);
  }

  useEffect(() => {
    if (files.length < 2) {
      setPrepared(null);
      return;
    }
    let cancelled = false;
    setBusy(true);
    setError("");
    setPrepared(null);
    void (async () => {
      try {
        const buffers = await Promise.all(files.map((file) => file.arrayBuffer()));
        const bytes = await mergePdfs(buffers);
        if (!cancelled) setPrepared(bytes);
      } catch (err) {
        if (!cancelled) {
          setPrepared(null);
          setError(err instanceof Error ? err.message : "Could not merge PDFs");
        }
      } finally {
        if (!cancelled) setBusy(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [files]);

  function save(bytes: Uint8Array) {
    const name = mergeDownloadName();
    downloadPdf(bytes, name);
    setSavedName(name);
  }

  function merge() {
    if (!prepared) {
      setError("Choose at least two PDF files to merge");
      return;
    }
    save(prepared);
  }

  return (
    <ToolCard title="Merge PDF">
      <p className="mt-3 text-sm leading-relaxed text-slate-600 dark:text-slate-400">
        Merge Amazon/Flipkart/Meesho PDF labels in the order you select. Choose multiple files, then download one
        print-ready PDF.
      </p>
      <FileRow logo={<MergePdfLogo />}>
        <input
          id="pdfFiles"
          type="file"
          accept="application/pdf,.pdf"
          multiple
          className="w-full text-sm file:mr-3 file:rounded-lg file:border file:border-slate-300 file:bg-white file:px-3 file:py-1.5"
          onChange={(event) => onChoose(event.target.files)}
        />
      </FileRow>
      {files.length > 0 && (
        <ol className="mt-3 list-decimal space-y-1 pl-5 text-sm text-slate-600 dark:text-slate-400">
          {files.map((file) => (
            <li key={`${file.name}-${file.size}-${file.lastModified}`}>{file.name}</li>
          ))}
        </ol>
      )}
      <button
        type="button"
        onClick={merge}
        disabled={busy || !prepared}
        className="mt-5 rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-50"
      >
        {busy ? "Merging..." : "Merge PDFs"}
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
      <div className="mt-6 flex flex-wrap gap-3">
        <FlipkartToolLink />
        <MeeshoToolLink />
      </div>
    </ToolCard>
  );
}
