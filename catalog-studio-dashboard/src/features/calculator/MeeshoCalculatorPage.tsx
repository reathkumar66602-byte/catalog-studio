import { useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import { calculateMeeshoProfit, inrExact, MEESHO_CATEGORIES } from "./meeshoMath";
import { promoLabel } from "../site/format";
import { useSite } from "../site/useSite";

export function MeeshoCalculatorPage() {
  const [categoryId, setCategoryId] = useState<(typeof MEESHO_CATEGORIES)[number]["id"]>("sarees");
  const [sellingPrice, setSellingPrice] = useState(280);
  const [productCost, setProductCost] = useState(200);
  const [gstOnProduct, setGstOnProduct] = useState(5);
  const [commissionPct, setCommissionPct] = useState(0);
  const [shipping, setShipping] = useState(65);
  const [packing, setPacking] = useState(5);
  const [returnRate, setReturnRate] = useState(20);
  const [rtoReturnRate, setRtoReturnRate] = useState(10);
  const [ads, setAds] = useState(0);
  const [gstRegistered, setGstRegistered] = useState(false);
  const [dailyUnits, setDailyUnits] = useState(100);
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [categoryQuery, setCategoryQuery] = useState("");

  const category = MEESHO_CATEGORIES.find((item) => item.id === categoryId) ?? MEESHO_CATEGORIES[0];
  const filtered = MEESHO_CATEGORIES.filter((item) =>
    item.label.toLowerCase().includes(categoryQuery.trim().toLowerCase()),
  );

  const result = useMemo(
    () =>
      calculateMeeshoProfit({
        sellingPrice,
        productCost,
        gstOnProduct,
        commissionPct,
        shipping,
        packing,
        returnRate,
        rtoReturnRate,
        ads,
        gstRegistered,
        dailyUnits,
      }),
    [
      sellingPrice,
      productCost,
      gstOnProduct,
      commissionPct,
      shipping,
      packing,
      returnRate,
      rtoReturnRate,
      ads,
      gstRegistered,
      dailyUnits,
    ],
  );

  function pickCategory(id: (typeof MEESHO_CATEGORIES)[number]["id"]) {
    const next = MEESHO_CATEGORIES.find((item) => item.id === id);
    if (!next) return;
    setCategoryId(next.id);
    setShipping(next.shipping);
    setCommissionPct(next.commission);
    setCategoryOpen(false);
    setCategoryQuery("");
  }

  const profitPositive = result.profit >= 0;
  const publicPage = useLocation().pathname === "/calculator";
  const site = useSite();
  const promo = site.promoCodes[0];

  return (
    <div
      className={
        publicPage
          ? "bg-[#fbf7f2] px-4 py-8 md:px-8 md:py-10"
          : "-m-4 min-h-full bg-[#fbf7f2] px-4 py-8 md:-m-8 md:px-8 md:py-10"
      }
    >
      <div className="mx-auto max-w-5xl">
        <div className="max-w-2xl">
          <h1 className="text-3xl font-semibold tracking-tight text-slate-900 md:text-4xl">Meesho Profit Calculator</h1>
          <p className="mt-3 text-sm leading-relaxed text-slate-600 md:text-base">
            Uses the same settlement math as the Meesho price calculation sheet: GST and returns on selling price,
            packing, ads, RTO, daily-to-monthly units, and the Meesho buyer price (selling + shipping).
            {site.client ? ` ${site.client.storeName} uses this calculator alongside label crop and the Chrome extension.` : ""}
          </p>
          {promo && (
            <p className="mt-3 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
              Store promo <span className="font-mono font-semibold">{promo.code}</span>
              {" — "}
              {promoLabel(promo.discountType, promo.discountValue, promo.trialDays)}
              {promo.description ? `. ${promo.description}` : ""}
            </p>
          )}
        </div>

        <div className="mt-8 grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
          <form className="space-y-4" onSubmit={(event) => event.preventDefault()}>
            <label className="block text-sm font-semibold text-slate-800">
              Product category (auto-fills shipping)
              <div className="relative mt-1.5">
                <button
                  type="button"
                  onClick={() => setCategoryOpen((open) => !open)}
                  className="flex w-full items-center justify-between rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-left text-sm text-slate-700"
                >
                  <span>{category.label}</span>
                  <span className="text-slate-400">▾</span>
                </button>
                {categoryOpen && (
                  <div className="absolute z-20 mt-1 w-full overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg">
                    <input
                      autoFocus
                      value={categoryQuery}
                      onChange={(event) => setCategoryQuery(event.target.value)}
                      placeholder="Search & select a category..."
                      className="w-full border-b border-slate-100 px-3 py-2 text-sm outline-none"
                    />
                    <ul className="max-h-56 overflow-auto py-1">
                      {filtered.map((item) => (
                        <li key={item.id}>
                          <button
                            type="button"
                            onClick={() => pickCategory(item.id)}
                            className="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50"
                          >
                            {item.label}
                            <span className="ml-2 text-xs text-slate-400">₹{item.shipping} shipping</span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            </label>

            <Field label="Selling price (₹)" value={sellingPrice} onChange={setSellingPrice} />
            <Field label="Your product cost (₹)" value={productCost} onChange={setProductCost} />

            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm font-semibold text-slate-800">
                GST on products (%)
                <select
                  value={gstOnProduct}
                  onChange={(event) => setGstOnProduct(Number(event.target.value))}
                  className="mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm"
                >
                  <option value={0}>0%</option>
                  <option value={5}>5%</option>
                  <option value={12}>12%</option>
                  <option value={18}>18%</option>
                  <option value={28}>28%</option>
                </select>
              </label>
              <Field label="Commission (%)" value={commissionPct} onChange={setCommissionPct} step="0.1" />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Shipping charge (₹)" value={shipping} onChange={setShipping} />
              <Field label="Packing (₹)" value={packing} onChange={setPacking} />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Return (%)" value={returnRate} onChange={setReturnRate} step="0.1" />
              <Field label="RTO return (%)" value={rtoReturnRate} onChange={setRtoReturnRate} step="0.1" />
            </div>

            <Field label="Ads (₹)" value={ads} onChange={setAds} />

            <label className="flex items-start gap-3 rounded-xl py-1 text-sm text-slate-700">
              <span>
                <span className="font-semibold text-slate-900">I am GST registered</span>
                <span className="mt-0.5 block text-xs text-slate-500">
                  Claims input tax credit (ITC) on GST deducted above
                </span>
              </span>
              <input
                type="checkbox"
                checked={gstRegistered}
                onChange={(event) => setGstRegistered(event.target.checked)}
                className="mt-1 h-4 w-4 accent-teal-700"
              />
            </label>

            <Field label="In a day sell" value={dailyUnits} onChange={setDailyUnits} />
            <p className="text-xs text-slate-500">
              In a month sell is 30 × daily = <span className="font-medium text-slate-700">{result.monthlyUnits}</span>
            </p>
          </form>

          <div className="space-y-4">
            <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
              <div className="border-b border-dashed border-slate-200 px-5 py-4">
                <p className="text-[11px] font-semibold tracking-[0.18em] text-slate-400">MEESHO · PER-ORDER ESTIMATE</p>
              </div>
              <dl className="space-y-3 px-5 py-4 text-sm">
                <Row label="Selling price" value={inrExact(sellingPrice)} />
                <Row label={`Commission (${commissionPct}%)`} value={money(result.commission)} negative={result.commission > 0} />
                <Row label="Ads" value={money(result.ads)} negative={result.ads > 0} />
                <Row label={`GST (${gstOnProduct}%)`} value={money(result.gst)} negative />
                <Row label={`Return (${returnRate}%)`} value={money(result.returns)} negative />
                <Row label="Product cost" value={money(productCost)} negative />
                <Row label="Packing" value={money(packing)} negative />
                <Row label={`RTO return (${rtoReturnRate}%)`} value={money(result.rtoReturn)} negative />
                {gstRegistered && <Row label="ITC claimed" value={`+ ${inrExact(result.gst)}`} positive />}
              </dl>
              <div className="border-t border-dashed border-slate-200 px-5 py-4">
                <div className="flex items-center justify-between text-sm font-semibold">
                  <span>Profit & loss approx</span>
                  <span className={profitPositive ? "text-emerald-600" : "text-rose-600"}>
                    {inrExact(result.profit)} ({result.margin}%)
                  </span>
                </div>
                <div className="mt-3 flex items-center justify-between text-sm">
                  <span className="text-slate-500">Meesho buyer price</span>
                  <span className="font-medium text-slate-900">{inrExact(result.buyerPrice)}</span>
                </div>
                <p className="mt-1 text-xs text-slate-400">Selling price + shipping charge</p>
              </div>
            </div>

            <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
              <p className="text-[11px] font-semibold tracking-[0.18em] text-slate-400">
                IF YOU SELL {result.monthlyUnits} PER MONTH
              </p>
              <div className="mt-4 grid grid-cols-2 gap-3">
                <div className="rounded-xl bg-slate-50 px-4 py-3">
                  <p className="text-xs text-slate-500">Monthly revenue</p>
                  <p className="mt-1 text-xl font-semibold text-slate-900">{inrExact(result.monthlyRevenue)}</p>
                </div>
                <div className="rounded-xl bg-emerald-50 px-4 py-3">
                  <p className="text-xs text-slate-500">Monthly profit approx</p>
                  <p className={`mt-1 text-xl font-semibold ${profitPositive ? "text-emerald-700" : "text-rose-600"}`}>
                    {inrExact(result.monthlyProfit)}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  step = "1",
}: {
  label: string;
  value: number;
  onChange: (value: number) => void;
  step?: string;
}) {
  return (
    <label className="block text-sm font-semibold text-slate-800">
      {label}
      <input
        type="number"
        min={0}
        step={step}
        value={Number.isFinite(value) ? value : 0}
        onChange={(event) => onChange(Number(event.target.value))}
        className="mt-1.5 w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm outline-none focus:border-teal-600"
      />
    </label>
  );
}

function Row({
  label,
  value,
  negative,
  positive,
}: {
  label: string;
  value: string;
  negative?: boolean;
  positive?: boolean;
}) {
  return (
    <div className="flex items-center justify-between gap-4">
      <dt className="text-slate-500">{label}</dt>
      <dd className={negative ? "text-slate-700" : positive ? "text-emerald-600" : "font-medium text-slate-900"}>
        {negative ? `− ${value}` : value}
      </dd>
    </div>
  );
}

function money(value: number) {
  return inrExact(value);
}
