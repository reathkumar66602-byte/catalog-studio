import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import type { AnalysisResult, ApiResponse } from "../../types";
import { Upload, X } from "lucide-react";
import { useI18n } from "../../i18n/LanguageProvider";

type FileItem = { file: File; preview: string; primary: boolean };

export function AnalyzePage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [files, setFiles] = useState<FileItem[]>([]);
  const [marketplace, setMarketplace] = useState("MEESHO");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    if (!loading) return;
    setProgress(8);
    const ticks = [18, 28, 38, 48, 58, 68, 76, 84, 90, 94];
    let index = 0;
    const timer = window.setInterval(() => {
      if (index >= ticks.length) {
        window.clearInterval(timer);
        return;
      }
      setProgress(ticks[index]);
      index += 1;
    }, 900);
    return () => window.clearInterval(timer);
  }, [loading]);

  function addFiles(list: FileList | null) {
    if (!list) return;
    const next = [...files];
    for (const file of Array.from(list)) {
      if (next.length >= 5) break;
      next.push({ file, preview: URL.createObjectURL(file), primary: next.length === 0 });
    }
    setFiles(next);
  }

  async function analyze() {
    if (files.length === 0) {
      setError(t("analyze.needImage"));
      return;
    }
    setLoading(true);
    setProgress(5);
    setError("");
    const form = new FormData();
    files.forEach((item) => form.append("images", item.file));
    form.append("primaryIndex", String(Math.max(0, files.findIndex((f) => f.primary))));
    form.append("marketplace", marketplace);
    try {
      const { data } = await api.post<ApiResponse<AnalysisResult>>("/product/analyze", form);
      setProgress(100);
      sessionStorage.setItem("cs_analysis", JSON.stringify(data.data));
      navigate(`/products/${data.data.productId}/review`);
    } catch (err) {
      setError(apiErrorMessage(err, t("analyze.failed")));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">{t("analyze.title")}</h1>
        <p className="text-slate-500">{t("analyze.subtitle")}</p>
      </div>
      <div
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          addFiles(e.dataTransfer.files);
        }}
        className="rounded-3xl border-2 border-dashed border-slate-300 bg-white p-10 text-center dark:border-slate-700 dark:bg-slate-900"
      >
        <Upload className="mx-auto text-teal-700" />
        <p className="mt-3 font-medium">{t("analyze.drop")}</p>
        <p className="text-sm text-slate-500">{t("analyze.dropHint")}</p>
        <label className="mt-4 inline-block cursor-pointer rounded-xl bg-teal-700 px-4 py-2 text-sm text-white">
          {t("analyze.chooseFiles")}
          <input type="file" accept="image/jpeg,image/png,image/webp" multiple className="hidden" onChange={(e) => addFiles(e.target.files)} />
        </label>
      </div>
      {files.length > 0 && (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-5">
          {files.map((item, index) => (
            <div key={item.preview} className={`relative overflow-hidden rounded-2xl border ${item.primary ? "border-teal-700" : "border-slate-200"}`}>
              <img src={item.preview} alt="" className="h-32 w-full object-cover" />
              <button className="absolute right-2 top-2 rounded-full bg-white p-1" onClick={() => setFiles(files.filter((_, i) => i !== index))}>
                <X size={14} />
              </button>
              <button
                className="absolute bottom-2 left-2 rounded-full bg-white/90 px-2 py-1 text-xs"
                onClick={() => setFiles(files.map((f, i) => ({ ...f, primary: i === index })))}
              >
                {item.primary ? t("analyze.mainImage") : t("analyze.setMain")}
              </button>
            </div>
          ))}
        </div>
      )}
      <div className="flex flex-wrap items-center gap-3">
        <select value={marketplace} onChange={(e) => setMarketplace(e.target.value)} className="rounded-xl border border-slate-200 px-3 py-2">
          <option value="MEESHO">Meesho</option>
          <option value="AMAZON">Amazon</option>
          <option value="FLIPKART">Flipkart</option>
        </select>
        <button onClick={analyze} disabled={loading} className="rounded-xl bg-teal-700 px-5 py-2.5 font-medium text-white disabled:opacity-60">
          {loading ? t("analyze.analyzingPct", { pct: progress }) : t("analyze.analyzeBtn")}
        </button>
      </div>
      {loading && (
        <div className="space-y-2 rounded-2xl border border-slate-200 bg-white px-4 py-3">
          <div className="flex items-center justify-between text-sm">
            <span className="font-medium text-slate-800">{t("analyze.analyzing")}</span>
            <span className="tabular-nums font-semibold text-teal-800">{progress}%</span>
          </div>
          <div className="h-2.5 overflow-hidden rounded-full bg-slate-100">
            <div
              className="h-full rounded-full bg-teal-600 transition-[width] duration-500 ease-out"
              style={{ width: `${Math.min(progress, 100)}%` }}
            />
          </div>
          <p className="text-xs text-slate-500">{t("analyze.progressHint")}</p>
        </div>
      )}
      {error && <p className="text-sm text-red-600">{error}</p>}
    </div>
  );
}
