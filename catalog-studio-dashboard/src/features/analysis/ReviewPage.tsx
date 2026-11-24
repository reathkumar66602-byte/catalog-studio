import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import type { AnalysisResult, Product } from "../../types";
import { useI18n } from "../../i18n/LanguageProvider";

const fields = [
  ["productType", "Product type"],
  ["category", "SEO category"],
  ["subCategory", "Subcategory"],
  ["gender", "Gender"],
  ["primaryColor", "Primary color"],
  ["pattern", "Pattern"],
  ["material", "Material"],
  ["sleeveType", "Sleeve type"],
  ["neckType", "Neck type"],
  ["collarType", "Collar type"],
  ["fit", "Fit"],
  ["occasion", "Occasion"],
  ["style", "Style"],
] as const;

export function ReviewPage() {
  const { t } = useI18n();
  const { id } = useParams();
  const navigate = useNavigate();
  const [analysis, setAnalysis] = useState<AnalysisResult | null>(null);
  const [form, setForm] = useState<Record<string, string>>({});
  const [descriptions, setDescriptions] = useState<string[]>(["", "", "", ""]);
  const [keywords, setKeywords] = useState("");
  const [titles, setTitles] = useState<string[]>([]);
  const [selectedTitle, setSelectedTitle] = useState(0);
  const [error, setError] = useState("");

  useEffect(() => {
    const cached = sessionStorage.getItem("cs_analysis");
    if (cached) {
      const parsed = JSON.parse(cached) as AnalysisResult;
      apply(parsed);
      return;
    }
    api.get(`/products/${id}`).then(({ data }) => {
      const product = data.data as Product;
      setForm({
        productType: product.productType || "",
        category: product.category || "",
        subCategory: product.subcategory || "",
        gender: product.gender || "",
        primaryColor: product.primaryColor || "",
        pattern: product.pattern || "",
        material: product.material || "",
        sleeveType: product.sleeveType || "",
        neckType: product.neckType || "",
        collarType: product.collarType || "",
        fit: product.fit || "",
        occasion: product.occasion || "",
        style: product.style || "",
      });
      setDescriptions(padDescriptions([product.description || ""]));
      setKeywords("");
      setTitles(product.titles?.map((row) => row.title) || [product.name]);
    });
  }, [id]);

  function padDescriptions(values: string[]) {
    const next = [...values.filter((item) => item != null), "", "", "", ""].slice(0, 4);
    while (next.length < 4) next.push("");
    return next;
  }

  function apply(result: AnalysisResult) {
    setAnalysis(result);
    const p = result.product;
    setForm({
      productType: p.productType || "",
      category: p.category || "",
      subCategory: p.subCategory || "",
      gender: p.gender || "",
      primaryColor: p.primaryColor || "",
      pattern: p.pattern || "",
      material: p.material || "",
      sleeveType: p.sleeveType || "",
      neckType: p.neckType || "",
      collarType: p.collarType || "",
      fit: p.fit || "",
      occasion: p.occasion || "",
      style: p.style || "",
    });
    const fromAi = p.suggestedDescriptions?.filter((item) => item?.trim()) || [];
    if (fromAi.length > 0) {
      setDescriptions(padDescriptions(fromAi));
    } else {
      setDescriptions(padDescriptions([p.productDescription || ""]));
    }
    setKeywords((p.keywords || []).join(", "));
    setTitles(p.suggestedTitles || []);
  }

  function badge(field: string) {
    const uncertain = analysis?.product.uncertainFields?.includes(field);
    const conf =
      field === "material"
        ? analysis?.product.materialConfidence
        : field === "pattern"
          ? analysis?.product.patternConfidence
          : field === "primaryColor"
            ? analysis?.product.colorConfidence
            : analysis?.product.overallConfidence;
    if (uncertain || (conf != null && conf < 0.6)) return { label: t("review.lowConf"), className: "bg-amber-100 text-amber-800" };
    if (conf != null && conf >= 0.85) return { label: t("review.highConf"), className: "bg-emerald-100 text-emerald-800" };
    return { label: t("review.medConf"), className: "bg-slate-100 text-slate-700" };
  }

  async function save(status: "DRAFT" | "ACTIVE", next?: string) {
    setError("");
    const primaryDescription =
      descriptions.find((item) => item.trim()) || descriptions[0] || "";
    try {
      await api.put(`/products/${id}`, {
        name: titles[selectedTitle] || form.productType,
        ...form,
        subcategory: form.subCategory,
        description: primaryDescription,
        status,
        titles,
        keywords: keywords
          .split(",")
          .map((item) => item.trim())
          .filter(Boolean),
        suggestedDescriptions: descriptions.map((item) => item.trim()).filter(Boolean),
      });
      navigate(next || "/products");
    } catch (err) {
      setError(apiErrorMessage(err, t("review.saveFail")));
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[240px_1fr]">
      <aside className="space-y-3">
        {(analysis?.product.images || []).map((img) => (
          <img key={img.id} src={img.url} alt="" className="w-full rounded-2xl object-cover" />
        ))}
      </aside>
      <section className="space-y-6 rounded-3xl bg-white p-6 dark:bg-slate-900">
        <div className="flex items-center justify-between">
          <h1 className="text-xl font-semibold">{t("review.title")}</h1>
          <p className="text-sm text-slate-500">{t("review.subtitle")}</p>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          {fields.map(([key, label]) => {
            const meta = badge(key);
            return (
              <label key={key} className="text-sm">
                <span className="mb-1 flex items-center justify-between">
                  {label}
                  <span className={`rounded-full px-2 py-0.5 text-[11px] ${meta.className}`}>{meta.label}</span>
                </span>
                <input
                  value={form[key] || ""}
                  onChange={(e) => setForm({ ...form, [key]: e.target.value })}
                  className={`w-full rounded-xl border px-3 py-2 ${analysis?.product.uncertainFields?.includes(key) ? "border-amber-400" : "border-slate-200"}`}
                />
              </label>
            );
          })}
        </div>
        <div>
          <h2 className="mb-2 font-medium">{t("review.suggestedTitles")}</h2>
          <div className="space-y-2">
            {titles.map((title, index) => (
              <label key={index} className="flex items-center gap-3 rounded-xl border border-slate-200 px-3 py-2">
                <input type="radio" checked={selectedTitle === index} onChange={() => setSelectedTitle(index)} />
                <input
                  className="flex-1 border-none bg-transparent outline-none"
                  value={title}
                  onChange={(e) => setTitles(titles.map((row, i) => (i === index ? e.target.value : row)))}
                />
              </label>
            ))}
          </div>
        </div>
        <label className="block text-sm">
          {t("review.seoKeywords")}
          <input
            value={keywords}
            onChange={(e) => setKeywords(e.target.value)}
            placeholder={t("review.seoKeywordsPh")}
            className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2"
          />
        </label>
        <div className="space-y-3">
          <h2 className="font-medium">{t("review.fourDescriptions")}</h2>
          {descriptions.map((value, index) => (
            <label key={index} className="block text-sm">
              {t("review.descriptionN", { n: index + 1 })}
              <textarea
                value={value}
                onChange={(e) =>
                  setDescriptions(descriptions.map((row, i) => (i === index ? e.target.value : row)))
                }
                rows={3}
                className="mt-1 w-full rounded-xl border border-slate-200 px-3 py-2"
              />
            </label>
          ))}
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex flex-wrap gap-3">
          <button onClick={() => save("DRAFT")} className="rounded-xl border border-slate-300 px-4 py-2">
            {t("review.saveDraft")}
          </button>
          <button onClick={() => save("ACTIVE")} className="rounded-xl bg-slate-900 px-4 py-2 text-white">
            {t("review.saveProduct")}
          </button>
          <button onClick={() => save("ACTIVE", "/extension")} className="rounded-xl bg-teal-700 px-4 py-2 text-white">
            {t("review.continueMp")}
          </button>
        </div>
      </section>
    </div>
  );
}
