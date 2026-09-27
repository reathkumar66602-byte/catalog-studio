type ParentCompanyMarkProps = {
  /** Compact inline mark for headers; band for landing; footer for legal line. */
  variant?: "inline" | "band" | "footer";
  className?: string;
  productName?: string;
};

function ShritajiLogo({ size = "md" }: { size?: "sm" | "md" | "lg" }) {
  const box = size === "lg" ? "h-14 w-14" : size === "sm" ? "h-8 w-8" : "h-11 w-11";
  return (
    <img
      src="/shritaji-mark.svg"
      alt=""
      aria-hidden
      className={`shrink-0 rounded-[14px] shadow-sm ring-1 ring-black/5 ${box}`}
    />
  );
}

/**
 * Parent-company attribution for Shritaji (official mark from D:\\shritaji assets).
 * Catalog Studio stays the product brand; Shritaji is the parent entity.
 */
export function ParentCompanyMark({
  variant = "band",
  className = "",
  productName = "Catalog Studio",
}: ParentCompanyMarkProps) {
  if (variant === "inline") {
    return (
      <p
        className={`flex items-center gap-1.5 text-[10px] font-medium uppercase tracking-[0.2em] text-slate-400 ${className}`.trim()}
      >
        <span className="h-px w-3 bg-slate-300" aria-hidden />
        A <span className="text-[#0D5C63]">Shritaji</span> Company
      </p>
    );
  }

  if (variant === "footer") {
    return (
      <div className={`flex flex-col items-center gap-3 ${className}`.trim()}>
        <div className="flex items-center gap-3">
          <ShritajiLogo size="sm" />
          <div className="text-left">
            <p className="text-[10px] font-semibold uppercase tracking-[0.24em] text-slate-400">Parent Company</p>
            <p className="text-sm font-semibold tracking-wide text-[#0D5C63]">Shritaji</p>
          </div>
        </div>
        <p className="text-xs text-slate-500">
          {productName} is a product of <span className="font-semibold text-slate-700">Shritaji</span>
        </p>
      </div>
    );
  }

  return (
    <div className={`relative ${className}`.trim()}>
      <div className="mb-8 flex items-center gap-4" aria-hidden>
        <div className="h-px flex-1 bg-gradient-to-r from-transparent via-slate-300 to-transparent" />
        <span className="text-[10px] font-semibold uppercase tracking-[0.35em] text-slate-400">
          From our parent company
        </span>
        <div className="h-px flex-1 bg-gradient-to-r from-transparent via-slate-300 to-transparent" />
      </div>

      <div className="relative mx-auto max-w-xl overflow-hidden rounded-[1.75rem] border border-slate-200/90 bg-white/90 px-8 py-10 text-center shadow-[0_20px_50px_-28px_rgba(15,23,42,0.35)]">
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top,rgba(13,92,99,0.12),transparent_55%)]"
        />
        <div className="relative flex flex-col items-center">
          <div className="flex items-center gap-3">
            <ShritajiLogo size="lg" />
            <span className="text-3xl font-bold tracking-tight text-[#0D5C63] sm:text-4xl">Shritaji</span>
          </div>
          <p className="mt-5 text-[11px] font-semibold uppercase tracking-[0.32em] text-slate-400">Parent Company</p>
          <div aria-hidden className="mt-4 h-px w-16 bg-gradient-to-r from-transparent via-[#1A8A7A] to-transparent" />
          <p className="mt-4 max-w-sm text-sm leading-relaxed text-slate-500">
            {productName} is built and operated by Shritaji — committed to trust, fairness, and lasting product quality.
          </p>
          <p className="mt-5 inline-flex items-center gap-2 rounded-full border border-[#0D5C63]/20 bg-[#0D5C63]/5 px-4 py-1.5 text-[11px] font-medium tracking-wide text-[#0D5C63]">
            <ShritajiLogo size="sm" />
            A Shritaji Company
          </p>
        </div>
      </div>
    </div>
  );
}
