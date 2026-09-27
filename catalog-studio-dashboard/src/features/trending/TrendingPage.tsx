import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { ChevronDown, ExternalLink, ImageOff, Search, Star } from "lucide-react";
import { api, apiErrorMessage } from "../../api/client";
import { useI18n } from "../../i18n/LanguageProvider";

type Category = { key: string; label: string; children?: Category[] };
type Product = {
  externalId: string;
  title: string;
  brand?: string | null;
  priceLabel?: string | null;
  mrpLabel?: string | null;
  rating?: string | null;
  reviewCount?: string | null;
  imageUrl?: string | null;
  productUrl: string;
};
type Page = {
  marketplace: string;
  category: string;
  categoryLabel: string;
  page: number;
  pageSize: number;
  cached: boolean;
  hasMore: boolean;
  products: Product[];
};

function chipClass(selected: boolean) {
  return `rounded-full border px-2.5 py-1 text-left text-xs ${
    selected
      ? "border-teal-700 bg-teal-700 font-medium text-white"
      : "border-slate-200 bg-slate-50 text-slate-600 hover:border-teal-600 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200"
  }`;
}

const MARKETS = [
  { id: "FLIPKART", label: "Flipkart" },
  { id: "AMAZON", label: "Amazon" },
  { id: "MEESHO", label: "Meesho" },
] as const;

export function TrendingPage() {
  const { t } = useI18n();
  const [marketplace, setMarketplace] = useState<(typeof MARKETS)[number]["id"]>("FLIPKART");
  const [category, setCategory] = useState("all");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState<number | undefined>(undefined);
  const [openKey, setOpenKey] = useState<string | null>(null);

  const categories = useQuery({
    queryKey: ["trending-categories"],
    queryFn: async () => (await api.get("/trending/categories")).data.data as Category[],
    staleTime: 60 * 60 * 1000,
  });

  const products = useQuery({
    queryKey: ["trending-products", marketplace, category, page ?? "saved"],
    queryFn: async () =>
      (
        await api.get("/trending/products", {
          params: {
            marketplace,
            category,
            ...(page === undefined ? {} : { page }),
          },
        })
      ).data.data as Page,
  });

  const groups = categories.data || [];
  const needle = query.trim().toLowerCase();
  const visible = useMemo(() => {
    if (!needle) return groups;
    return groups
      .map((group) => {
        const parentHit = group.label.toLowerCase().includes(needle);
        const children = (group.children || []).filter(
          (child) => parentHit || child.label.toLowerCase().includes(needle),
        );
        if (!parentHit && children.length === 0) return null;
        return { ...group, children };
      })
      .filter((group): group is Category => group !== null);
  }, [groups, needle]);
  const view = products.data;
  const from = view && view.products.length > 0 ? view.page * view.pageSize + 1 : 0;
  const to = view ? view.page * view.pageSize + view.products.length : 0;

  function pickMarket(next: (typeof MARKETS)[number]["id"]) {
    setMarketplace(next);
    setPage(undefined);
  }

  function pickCategory(key: string) {
    setCategory(key);
    setPage(undefined);
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">{t("trend.title")}</h1>
        <p className="mt-1 max-w-3xl text-sm text-slate-500">{t("trend.subtitle")}</p>
      </div>

      <div className="flex flex-wrap gap-2">
        {MARKETS.map((market) => (
          <button
            key={market.id}
            type="button"
            onClick={() => pickMarket(market.id)}
            className={`rounded-full px-4 py-2 text-sm font-medium ${
              marketplace === market.id
                ? "bg-teal-700 text-white"
                : "border border-slate-200 bg-white text-slate-700 hover:border-teal-600 dark:border-slate-700 dark:bg-slate-900"
            }`}
          >
            {market.label}
          </button>
        ))}
      </div>

      <div className="grid gap-6 lg:grid-cols-[320px_minmax(0,1fr)]">
        <aside className="rounded-2xl border border-slate-200 bg-white p-3 dark:border-slate-800 dark:bg-slate-900">
          <label className="relative block">
            <Search size={16} className="pointer-events-none absolute left-3 top-2.5 text-slate-400" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t("trend.search")}
              className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2 pl-9 pr-3 text-sm outline-none focus:border-teal-600 dark:border-slate-700 dark:bg-slate-950"
            />
          </label>
          <div className="mt-3 max-h-[36rem] space-y-1 overflow-y-auto pr-1">
            {visible.map((group) => {
              const children = group.children || [];
              const expanded = children.length > 0 && (Boolean(needle) || openKey === group.key);
              const parentSelected = category === group.key;
              return (
                <div key={group.key} className="rounded-xl">
                  <button
                    type="button"
                    onClick={() => {
                      if (children.length === 0) {
                        pickCategory(group.key);
                        return;
                      }
                      setOpenKey(openKey === group.key ? null : group.key);
                    }}
                    className={`flex w-full items-center justify-between gap-2 rounded-xl px-3 py-2 text-left text-sm ${
                      parentSelected
                        ? "bg-teal-50 font-medium text-teal-800 dark:bg-teal-900/40 dark:text-teal-100"
                        : "text-slate-700 hover:bg-slate-50 dark:text-slate-200 dark:hover:bg-slate-800"
                    }`}
                  >
                    <span>{group.label}</span>
                    {children.length > 0 && (
                      <span className="inline-flex items-center gap-1 text-xs text-slate-400">
                        {children.length}
                        <ChevronDown size={14} className={expanded ? "rotate-180" : ""} />
                      </span>
                    )}
                  </button>
                  {expanded && (
                    <div className="flex flex-wrap gap-1.5 px-2 pb-2 pt-1">
                      <button
                        type="button"
                        onClick={() => {
                          setOpenKey(group.key);
                          pickCategory(group.key);
                        }}
                        className={chipClass(parentSelected)}
                      >
                        {t("trend.viewAll", { name: group.label })}
                      </button>
                      {children.map((child) => (
                        <button
                          key={child.key}
                          type="button"
                          onClick={() => {
                            setOpenKey(group.key);
                            pickCategory(child.key);
                          }}
                          className={chipClass(category === child.key)}
                        >
                          {child.label}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </aside>

        <section className="min-w-0 space-y-4">
          {view && (
            <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-slate-500">
              <p>{view.cached ? t("trend.saved") : t("trend.live")}</p>
              {view.products.length > 0 && <p>{t("trend.range", { from, to })}</p>}
            </div>
          )}

          {products.isLoading && !view && (
            <p className="rounded-2xl border border-slate-200 bg-white px-4 py-8 text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-900">
              {t("trend.loading")}
            </p>
          )}

          {products.isError && (
            <p className="rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
              {apiErrorMessage(products.error)}
            </p>
          )}

          {view && view.products.length === 0 && !products.isLoading && (
            <p className="rounded-2xl border border-slate-200 bg-white px-4 py-8 text-sm text-slate-500 dark:border-slate-800 dark:bg-slate-900">
              {t("trend.empty")}
            </p>
          )}

          {view && view.products.length > 0 && (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
              {view.products.map((product, index) => (
                <article
                  key={`${product.externalId}-${index}`}
                  className="flex flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-900"
                >
                  <ProductPhoto
                    src={product.imageUrl}
                    rank={view.page * view.pageSize + index + 1}
                    meesho={marketplace === "MEESHO"}
                  />
                  <div className="flex flex-1 flex-col gap-2 p-3">
                    <p className="line-clamp-2 text-sm font-medium">{product.title}</p>
                    {product.brand && <p className="text-xs text-slate-500">{product.brand}</p>}
                    <div className="mt-auto flex items-baseline gap-2">
                      {product.priceLabel && <p className="font-semibold">{product.priceLabel}</p>}
                      {product.mrpLabel && product.mrpLabel !== product.priceLabel && (
                        <p className="text-xs text-slate-400 line-through">{product.mrpLabel}</p>
                      )}
                    </div>
                    {(product.rating || product.reviewCount) && (
                      <p className="flex items-center gap-1 text-xs text-slate-500">
                        <Star size={12} className="text-amber-500" />
                        {product.rating || "—"}
                        {product.reviewCount ? ` · ${product.reviewCount}` : ""}
                      </p>
                    )}
                    <a
                      href={product.productUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center gap-1 text-sm font-medium text-teal-700 hover:underline"
                    >
                      {t("trend.open")}
                      <ExternalLink size={14} />
                    </a>
                  </div>
                </article>
              ))}
            </div>
          )}

          {view && (
            <div className="flex flex-wrap gap-3">
              <button
                type="button"
                disabled={view.page <= 0 || products.isFetching}
                onClick={() => setPage(Math.max(0, view.page - 1))}
                className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium disabled:opacity-40 dark:border-slate-700 dark:bg-slate-900"
              >
                {t("trend.prev")}
              </button>
              <button
                type="button"
                disabled={!view.hasMore || products.isFetching}
                onClick={() => setPage(view.page + 1)}
                className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-40"
              >
                {products.isFetching ? t("trend.loading") : t("trend.more")}
              </button>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}

function displayPhoto(src: string) {
  return src.replace(/_512\.(jpe?g|webp|png)$/i, ".$1");
}

function meeshoCatalogCover(src: string) {
  return /\/images\/catalogs\/\d+\/cover\//.test(src);
}

function ProductPhoto({ src, rank, meesho }: { src?: string | null; rank: number; meesho?: boolean }) {
  const [failed, setFailed] = useState(false);
  const photo = src ? (meesho ? displayPhoto(src) : src) : "";
  const show = Boolean(photo) && !failed;
  const cover = Boolean(meesho && show && meeshoCatalogCover(photo));
  return (
    <div
      className={`relative overflow-hidden bg-slate-50 dark:bg-slate-800 ${
        cover ? "aspect-[16/9]" : "aspect-[3/4]"
      }`}
    >
      {show ? (
        <img
          src={photo}
          alt=""
          className="h-full w-full object-contain"
          referrerPolicy="no-referrer"
          onError={() => setFailed(true)}
        />
      ) : (
        <div className="flex h-full items-center justify-center text-slate-300">
          <ImageOff size={28} />
        </div>
      )}
      <span className="absolute left-2 top-2 rounded-full bg-white/90 px-2 py-0.5 text-xs font-semibold text-slate-700">
        #{rank}
      </span>
    </div>
  );
}
