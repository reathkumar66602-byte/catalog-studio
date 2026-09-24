import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, Upload } from "lucide-react";
import { api, apiErrorMessage } from "../../api/client";
import type { ApiResponse, SubscriptionStatus } from "../../types";

type Preview = { file: File; url: string };

type ShootImage = {
  id: string;
  kind: string;
  url: string;
  downloadable: boolean;
  sortOrder: number;
};

type ShootResult = {
  id: string;
  mode: string;
  modelAge: string;
  marketplace: boolean;
  trialLimited: boolean;
  status: string;
  errorMessage?: string;
  images: ShootImage[];
  createdAt: string;
};

const AGE_GROUPS = [
  {
    label: "Baby & kids",
    ages: [
      ["0-1", "0–1 yrs"],
      ["1-2", "1–2 yrs"],
      ["3-4", "3–4 yrs"],
      ["5-6", "5–6 yrs"],
      ["7-8", "7–8 yrs"],
      ["9-10", "9–10 yrs"],
      ["11-12", "11–12 yrs"],
    ],
  },
  {
    label: "Teen",
    ages: [
      ["13-15", "13–15 yrs"],
      ["16-19", "16–19 yrs"],
    ],
  },
  {
    label: "Adult",
    ages: [
      ["20-25", "20–25 yrs"],
      ["26-32", "26–32 yrs"],
      ["33-40", "33–40 yrs"],
      ["41-50", "41–50 yrs"],
      ["50+", "50+ yrs"],
    ],
  },
] as const;

const ANGLES = [
  { id: "FRONT", label: "Front" },
  { id: "BACK", label: "Back" },
  { id: "SIDE", label: "Side" },
  { id: "SHOP", label: "Shop shot" },
] as const;

const KIND_LABEL: Record<string, string> = {
  FRONT: "Front",
  BACK: "Back",
  SIDE: "Side",
  SHOP: "Shop shot",
  MARKETPLACE: "Flipkart / Amazon",
  MEESHO: "Meesho",
};

export function plannedImageCount(input: {
  angles: string[];
  flipkart: boolean;
  mode: "SINGLE" | "COMBO";
  productCount: number;
  trial: boolean;
}) {
  const total = input.angles.length;
  if (input.trial) return Math.min(total, 1);
  return total;
}

export function ShootPage() {
  const access = useQuery({
    queryKey: ["sub"],
    queryFn: async () => (await api.get("/subscriptions/current")).data.data as SubscriptionStatus,
  });

  const [mode, setMode] = useState<"SINGLE" | "COMBO">("SINGLE");
  const [front, setFront] = useState<Preview | null>(null);
  const [back, setBack] = useState<Preview | null>(null);
  const [products, setProducts] = useState<Preview[]>([]);
  const [modelAge, setModelAge] = useState("");
  const [angles, setAngles] = useState<string[]>(["FRONT"]);
  const [flipkart, setFlipkart] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<ShootResult | null>(null);

  const trial = Boolean(access.data?.trialActive);
  const backUnlocked = mode === "SINGLE" && Boolean(back);
  const imageCount = plannedImageCount({
    angles,
    flipkart,
    mode,
    productCount: mode === "COMBO" ? products.length : 1,
    trial,
  });

  function toggleAngle(id: string) {
    if (id === "BACK" && !backUnlocked) return;
    setAngles((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
  }

  function replacePreview(current: Preview | null, file: File | null) {
    if (current) URL.revokeObjectURL(current.url);
    return file ? { file, url: URL.createObjectURL(file) } : null;
  }

  async function generate() {
    if (!modelAge) {
      setError("Choose a model age");
      return;
    }
    if (mode === "SINGLE" && !front) {
      setError("Front photo is required");
      return;
    }
    if (mode === "COMBO" && products.length === 0) {
      setError("Upload at least one product photo");
      return;
    }
    if (angles.length === 0) {
      setError("Choose at least one photo to generate");
      return;
    }
    setLoading(true);
    setError("");
    const form = new FormData();
    form.append("mode", mode);
    form.append("modelAge", modelAge);
    form.append("flipkart", String(flipkart));
    angles.forEach((angle) => form.append("angles", angle));
    if (mode === "SINGLE" && front) form.append("front", front.file);
    if (mode === "SINGLE" && back) form.append("back", back.file);
    if (mode === "COMBO") products.forEach((item) => form.append("products", item.file));
    try {
      const { data } = await api.post<ApiResponse<ShootResult>>("/shoots", form, { timeout: 300000 });
      setResult(data.data);
    } catch (err) {
      setError(apiErrorMessage(err, "Could not generate catalog photos"));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <div>
        <h1 className="text-2xl font-semibold">Shoot</h1>
        <p className="text-slate-500">Turn a garment photo into catalog images with OpenAI. Colour, print, and design stay locked to your photo.</p>
      </div>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <Step n={1} title="Upload your photos" hint="Front required, back optional." />
        <div className="mt-4 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setMode("SINGLE")}
            className={mode === "SINGLE" ? "rounded-full bg-teal-700 px-4 py-2 text-sm font-medium text-white" : "rounded-full border border-slate-200 px-4 py-2 text-sm font-medium"}
          >
            Single product
          </button>
          <button
            type="button"
            onClick={() => setMode("COMBO")}
            className={mode === "COMBO" ? "rounded-full bg-teal-700 px-4 py-2 text-sm font-medium text-white" : "rounded-full border border-slate-200 px-4 py-2 text-sm font-medium"}
          >
            Combo · up to 6
          </button>
        </div>
        {mode === "SINGLE" ? (
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <Drop
              label="Front photo"
              required
              preview={front}
              onFile={(file) => setFront(replacePreview(front, file))}
            />
            <Drop
              label="Back photo"
              hint="Optional"
              preview={back}
              onFile={(file) => {
                const next = replacePreview(back, file);
                setBack(next);
                if (!next) setAngles((current) => current.filter((item) => item !== "BACK"));
              }}
            />
          </div>
        ) : (
          <div className="mt-4">
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {products.map((item, index) => (
                <div key={item.url} className="relative overflow-hidden rounded-2xl border border-slate-200">
                  <img src={item.url} alt="" className="h-36 w-full bg-slate-50 object-contain" />
                  <button
                    type="button"
                    className="absolute right-2 top-2 rounded-full bg-white px-2 py-1 text-xs"
                    onClick={() => {
                      URL.revokeObjectURL(item.url);
                      setProducts(products.filter((_, i) => i !== index));
                    }}
                  >
                    Remove
                  </button>
                </div>
              ))}
              {products.length < 6 && (
                <label className="flex h-36 cursor-pointer flex-col items-center justify-center rounded-2xl border border-dashed border-slate-300 bg-slate-50 text-sm text-slate-600">
                  <Upload size={18} />
                  Add product
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    className="hidden"
                    onChange={(event) => {
                      const file = event.target.files?.[0];
                      event.target.value = "";
                      if (!file || products.length >= 6) return;
                      setProducts([...products, { file, url: URL.createObjectURL(file) }]);
                    }}
                  />
                </label>
              )}
            </div>
            <p className="mt-2 text-xs text-slate-500">{products.length} of 6 photos</p>
          </div>
        )}
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <Step n={2} title="Model age" hint="Gender is read from your garment photo." />
        <p className="mt-4 text-sm font-medium">Model age <span className="text-red-600">*</span> <span className="font-normal text-slate-500">match the size you sell</span></p>
        <div className="mt-3 space-y-3">
          {AGE_GROUPS.map((group) => (
            <div key={group.label}>
              <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{group.label}</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {group.ages.map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    onClick={() => setModelAge(value)}
                    className={
                      modelAge === value
                        ? "rounded-full border border-teal-700 bg-teal-50 px-3 py-1.5 text-sm font-medium text-teal-900"
                        : "rounded-full border border-slate-200 px-3 py-1.5 text-sm"
                    }
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <Step n={3} title="What you'll get" hint={trial ? "Your free trial makes 1 image. A paid plan makes the full set." : "Every selected angle is generated from your garment photo."} />
        <div className="mt-4 flex flex-wrap gap-2">
          {ANGLES.map((angle) => {
            const locked = angle.id === "BACK" && !backUnlocked;
            const on = angles.includes(angle.id);
            return (
              <button
                key={angle.id}
                type="button"
                disabled={locked}
                onClick={() => toggleAngle(angle.id)}
                className={`rounded-full border px-3 py-1.5 text-sm ${
                  locked
                    ? "cursor-not-allowed border-slate-200 text-slate-400"
                    : on
                      ? "border-teal-700 bg-teal-50 font-medium text-teal-900"
                      : "border-slate-200"
                }`}
              >
                {on && !locked ? "✓ " : ""}
                {angle.label}
              </button>
            );
          })}
        </div>
        <p className="mt-3 text-sm text-slate-600">
          Product only is the same garment shot without any model — same background, same light, 100% the same product.
        </p>
        {!backUnlocked && mode === "SINGLE" && (
          <p className="mt-1 text-sm text-slate-500">Upload a back photo to unlock the back angle.</p>
        )}

        <label className="mt-4 flex cursor-pointer items-start gap-3 rounded-2xl border border-slate-200 px-4 py-3">
          <input
            type="checkbox"
            className="mt-1 h-4 w-4"
            checked={flipkart}
            onChange={(event) => setFlipkart(event.target.checked)}
          />
          <span>
            <span className="font-medium">Flipkart / Amazon</span>
            <span className="mt-1 block text-sm text-slate-500">
              White background on every photo. These images are for Flipkart and Amazon.
            </span>
          </span>
        </label>
        {!flipkart && (
          <p className="mt-3 text-sm text-slate-500">
            Left off, every photo uses a styled background for Meesho. Not a plain white backdrop.
          </p>
        )}

        <div className="mt-4 flex items-center justify-between rounded-2xl bg-slate-50 px-4 py-3 text-sm dark:bg-slate-800">
          <span>
            <span className="font-semibold">{imageCount} image{imageCount === 1 ? "" : "s"}</span>
            <span className="text-violet-700"> · {imageCount} credit{imageCount === 1 ? "" : "s"}</span>
          </span>
          {trial && <span className="text-slate-500">1 left on trial</span>}
        </div>
      </section>

      {error && <p className="text-sm text-red-600">{error}</p>}
      {result?.errorMessage && result.status === "PARTIAL" && (
        <p className="text-sm text-amber-800">{result.errorMessage}</p>
      )}
      {result?.trialLimited && (
        <p className="text-sm text-slate-600">Free trial generated 1 image. A paid plan generates every angle you select.</p>
      )}

      <button
        type="button"
        onClick={generate}
        disabled={loading || imageCount === 0}
        className="w-full rounded-2xl bg-slate-800 px-5 py-3 font-semibold text-white disabled:opacity-60"
      >
        {loading ? "Generating catalog photos…" : `Generate ${imageCount} image${imageCount === 1 ? "" : "s"}`}
      </button>

      {(result?.images.length ?? 0) > 0 && result && (
        <section className="grid gap-3 sm:grid-cols-2">
          {result.images.map((image) => (
            <figure key={image.id} className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
              <img src={image.url} alt={KIND_LABEL[image.kind] || "Catalog photo"} className="max-h-[720px] w-full bg-slate-50 object-contain" />
              <figcaption className="flex items-center justify-between px-3 py-2 text-sm">
                <span>{KIND_LABEL[image.kind] || image.kind}</span>
                <button type="button" className="inline-flex items-center gap-1 font-medium text-teal-800" onClick={() => downloadShootImage(result.id, image)}>
                  <Download size={14} /> Download
                </button>
              </figcaption>
            </figure>
          ))}
        </section>
      )}
    </div>
  );
}

function Step({ n, title, hint }: { n: number; title: string; hint: string }) {
  return (
    <div className="flex items-start gap-3">
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-teal-700 text-sm font-semibold text-white">{n}</span>
      <div>
        <h2 className="font-semibold">{title}</h2>
        <p className="text-sm text-slate-500">{hint}</p>
      </div>
    </div>
  );
}

function Drop({
  label,
  hint,
  required,
  preview,
  onFile,
}: {
  label: string;
  hint?: string;
  required?: boolean;
  preview: Preview | null;
  onFile: (file: File | null) => void;
}) {
  return (
    <label className="relative flex min-h-44 cursor-pointer flex-col items-center justify-center overflow-hidden rounded-2xl border border-dashed border-slate-300 bg-slate-50 text-center">
      {preview ? (
        <img src={preview.url} alt="" className="absolute inset-0 h-full w-full bg-slate-50 object-contain" />
      ) : (
        <>
          <Upload className="text-teal-700" />
          <span className="mt-2 text-sm font-medium">Tap to upload</span>
          <span className="text-xs text-slate-500">{required ? "Required" : hint}</span>
        </>
      )}
      <span className="relative z-10 mt-2 rounded-full bg-white/90 px-2 py-1 text-xs font-medium">
        {label} {required && <span className="text-red-600">*</span>}
      </span>
      <input
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0] || null;
          event.target.value = "";
          onFile(file);
        }}
      />
    </label>
  );
}

async function downloadShootImage(shootId: string, image: ShootImage) {
  const response = await api.get(`/shoots/${shootId}/images/${image.id}/download`, { responseType: "blob" });
  const header = response.headers["content-type"];
  const blob = new Blob([response.data], { type: typeof header === "string" ? header : "image/png" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = image.kind === "MARKETPLACE"
    ? `flipkart-amazon-${image.sortOrder}.png`
    : image.kind === "MEESHO"
      ? `meesho-${image.sortOrder}.png`
      : `${image.kind.toLowerCase()}.png`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
