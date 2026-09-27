type ParentCompanyMarkProps = {
  /** Compact inline mark for headers; full mark for landing / footer bands. */
  variant?: "inline" | "band" | "footer";
  className?: string;
  productName?: string;
};

/**
 * Corporate parent-company attribution for Shirtaji.
 * Catalog Studio stays the product brand; Shirtaji is shown as the parent entity.
 */
export function ParentCompanyMark({
  variant = "band",
  className = "",
  productName = "Catalog Studio",
}: ParentCompanyMarkProps) {
  if (variant === "inline") {
    return (
      <p
        className={`text-[10px] font-medium uppercase tracking-[0.22em] text-slate-400 ${className}`.trim()}
      >
        A <span className="text-teal-700">Shirtaji</span> Company
      </p>
    );
  }

  if (variant === "footer") {
    return (
      <p className={`text-xs text-slate-500 ${className}`.trim()}>
        {productName} is a product of{" "}
        <span className="font-semibold tracking-wide text-slate-700">Shirtaji</span>
      </p>
    );
  }

  return (
    <div
      className={`relative overflow-hidden rounded-2xl border border-teal-100 bg-gradient-to-br from-white via-teal-50/40 to-slate-50 px-6 py-8 shadow-[0_18px_40px_-28px_rgba(15,118,110,0.45)] ${className}`.trim()}
    >
      <div aria-hidden className="pointer-events-none absolute inset-y-0 left-0 w-1 bg-gradient-to-b from-teal-500 to-sky-500" />
      <div className="flex flex-col items-center gap-4 text-center sm:flex-row sm:items-center sm:justify-between sm:text-left">
        <div className="flex items-center gap-4">
          <span
            aria-hidden
            className="grid h-12 w-12 shrink-0 place-items-center rounded-2xl border border-teal-200 bg-white text-sm font-bold tracking-wider text-teal-800 shadow-sm"
          >
            SJ
          </span>
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-[0.28em] text-teal-700/70">
              Parent Company
            </p>
            <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900">Shirtaji</p>
            <p className="mt-0.5 text-sm text-slate-500">
              {productName} is proudly operated under Shirtaji.
            </p>
          </div>
        </div>
        <div className="hidden h-10 w-px bg-teal-100 sm:block" aria-hidden />
        <p className="max-w-xs text-xs leading-relaxed text-slate-500 sm:text-right">
          Built to company standards for trust, fairness, and lasting product quality.
        </p>
      </div>
    </div>
  );
}
