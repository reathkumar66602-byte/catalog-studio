import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { Search, Store } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import type { SubscriptionStatus } from "../../types";
import {
  canOpenSupplierStore,
  formatInr,
  priceRange,
  type PageResult,
  type ProductCard,
  type WholesaleCategory,
} from "./wholesale";
import { useAuth } from "../../store/auth";

const PAGE_SIZE = 24;

export function LiveStockPage() {
  const { user } = useAuth();
  const [category, setCategory] = useState("all");
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState("newest");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setQuery(search.trim());
      setPage(0);
    }, 350);
    return () => window.clearTimeout(timer);
  }, [search]);

  const access = useQuery({
    queryKey: ["sub"],
    queryFn: async () => (await api.get("/subscriptions/current")).data.data as SubscriptionStatus,
    staleTime: 30_000,
  });

  const categories = useQuery({
    queryKey: ["ws-categories"],
    queryFn: async () => (await api.get("/wholesale/categories")).data.data as WholesaleCategory[],
    staleTime: 60_000,
  });

  const products = useQuery({
    queryKey: ["ws-products", category, query, sort, page],
    queryFn: async () =>
      (
        await api.get("/wholesale/products", {
          params: { category: category === "all" ? undefined : category, q: query || undefined, sort, page, size: PAGE_SIZE },
        })
      ).data.data as PageResult<ProductCard>,
    placeholderData: keepPreviousData,
  });

  const totalCount = (categories.data || []).reduce((sum, item) => sum + item.productCount, 0);
  const items = products.data?.items || [];

  return (
    <div className="mx-auto max-w-7xl space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-teal-700 text-white">
            <Store size={20} />
          </span>
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-semibold">
              <span className="relative flex h-2.5 w-2.5">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-red-500 opacity-75" />
                <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-red-500" />
              </span>
              LIVE Stock
            </h1>
            <p className="text-sm text-slate-500">Wholesale stock from verified suppliers, updated in real time.</p>
          </div>
        </div>
        <div className="flex items-center gap-2 text-sm">
          <Link
            to="/live-stock/orders"
            className="rounded-xl border border-teal-200 px-3 py-2 font-medium text-teal-800 hover:bg-teal-50"
          >
            My orders
          </Link>
          {canOpenSupplierStore(access.data, user?.role) ? (
            <Link to="/supplier/store" className="rounded-xl bg-teal-700 px-3 py-2 font-semibold text-white">
              Create / manage store
            </Link>
          ) : (
            <Link to="/subscription" className="rounded-xl border border-slate-200 px-3 py-2 text-slate-600 hover:bg-slate-50">
              Become a supplier
            </Link>
          )}
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row">
        <label className="relative flex-1">
          <Search size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by product name or code"
            className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-9 pr-3 text-sm outline-none focus:border-teal-600 focus:ring-2 focus:ring-teal-100 dark:border-slate-700 dark:bg-slate-900"
          />
        </label>
        <select
          value={sort}
          onChange={(e) => {
            setSort(e.target.value);
            setPage(0);
          }}
          className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm dark:border-slate-700 dark:bg-slate-900"
          aria-label="Sort products"
        >
          <option value="newest">Newest first</option>
          <option value="popular">Most booked</option>
          <option value="price_asc">Price: low to high</option>
          <option value="price_desc">Price: high to low</option>
        </select>
      </div>

      <div className="flex gap-2 overflow-x-auto pb-1">
        <CategoryPill
          active={category === "all"}
          label="All"
          count={totalCount}
          onClick={() => {
            setCategory("all");
            setPage(0);
          }}
        />
        {(categories.data || []).map((item) => (
          <CategoryPill
            key={item.id}
            active={category === item.slug}
            label={item.name}
            count={item.productCount}
            onClick={() => {
              setCategory(item.slug);
              setPage(0);
            }}
          />
        ))}
      </div>

      {products.isError && <p className="text-sm text-red-600">{apiErrorMessage(products.error)}</p>}
      {products.isLoading && <p className="text-sm text-slate-500">Loading live stock...</p>}

      {!products.isLoading && items.length === 0 && (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-900">
          No products found{query ? ` for "${query}"` : ""}. Try another category or search.
        </div>
      )}

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        {items.map((product) => (
          <ProductCardView key={product.id} product={product} />
        ))}
      </div>

      {products.data && products.data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 pt-2 text-sm">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-lg border px-3 py-1.5 disabled:opacity-40"
          >
            Previous
          </button>
          <span className="text-slate-500">
            Page {products.data.page + 1} of {products.data.totalPages}
          </span>
          <button
            type="button"
            disabled={!products.data.hasNext}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-lg border px-3 py-1.5 disabled:opacity-40"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}

function CategoryPill({
  active,
  label,
  count,
  onClick,
}: {
  active: boolean;
  label: string;
  count: number;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex shrink-0 items-center gap-2 rounded-full border px-4 py-1.5 text-sm font-medium transition ${
        active
          ? "border-teal-700 bg-teal-700 text-white"
          : "border-slate-200 bg-white text-slate-700 hover:border-teal-300 hover:bg-teal-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
      }`}
    >
      {label}
      <span className={`rounded-full px-1.5 text-xs ${active ? "bg-white/20" : "bg-slate-100 text-slate-600 dark:bg-slate-800"}`}>
        {count}
      </span>
    </button>
  );
}

function ProductCardView({ product }: { product: ProductCard }) {
  const soldOut = product.totalStock <= 0;
  const showMrp = product.mrp && product.minPrice && product.mrp > product.minPrice;
  return (
    <Link
      to={`/live-stock/${product.slug}`}
      className="group overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition hover:border-teal-300 hover:shadow-md dark:border-slate-800 dark:bg-slate-900"
    >
      <div className="relative aspect-[3/4] bg-slate-100 dark:bg-slate-800">
        {product.primaryImageUrl ? (
          <img
            src={product.primaryImageUrl}
            alt={product.title}
            loading="lazy"
            className={`h-full w-full object-cover transition group-hover:scale-[1.02] ${soldOut ? "opacity-50" : ""}`}
          />
        ) : (
          <div className="flex h-full items-center justify-center text-xs text-slate-400">No photo</div>
        )}
        {product.bookedCount > 0 && (
          <span className="absolute left-2 top-2 rounded-full bg-teal-700 px-2 py-0.5 text-[11px] font-semibold text-white">
            {product.bookedCount} booked
          </span>
        )}
        {soldOut && (
          <span className="absolute right-2 top-2 rounded-full bg-red-600 px-2 py-0.5 text-[11px] font-semibold text-white">
            Sold out
          </span>
        )}
      </div>
      <div className="space-y-1.5 p-3">
        <p className="line-clamp-2 text-sm font-semibold leading-snug">{product.title}</p>
        <p className="text-xs text-slate-500">Code: {product.code}</p>
        <p className="flex items-baseline gap-2">
          <span className="text-base font-bold text-teal-800 dark:text-teal-300">
            {priceRange(product.minPrice, product.maxPrice)}
          </span>
          {showMrp && <span className="text-xs text-slate-400 line-through">{formatInr(product.mrp)}</span>}
        </p>
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-1">
            {product.colors.slice(0, 5).map((color) => (
              <span
                key={color.label}
                title={color.label}
                className="h-4 w-4 rounded-full border border-slate-300"
                style={{ backgroundColor: color.hex || undefined }}
              >
                {!color.hex && <span className="block text-center text-[9px] leading-4">{color.label.charAt(0)}</span>}
              </span>
            ))}
            {product.colors.length > 5 && <span className="text-[11px] text-slate-500">+{product.colors.length - 5}</span>}
          </div>
          <span className={`text-xs font-medium ${soldOut ? "text-red-600" : "text-emerald-700"}`}>
            {soldOut ? "0 pcs" : `${product.totalStock} pcs`}
          </span>
        </div>
        {product.storeName && <p className="truncate text-[11px] text-slate-400">{product.storeName}</p>}
      </div>
    </Link>
  );
}
