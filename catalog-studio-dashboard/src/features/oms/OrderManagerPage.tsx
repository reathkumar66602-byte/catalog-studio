import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { api, apiErrorMessage } from "../../api/client";
import { useI18n } from "../../i18n/LanguageProvider";

type ChannelOrder = {
  id: string;
  externalOrderId: string;
  externalSubOrderId?: string | null;
  status: string;
  sku?: string | null;
  quantity: number;
  buyerName?: string | null;
  shippingCity?: string | null;
  awb?: string | null;
  labelStatus: string;
  labelUrl?: string | null;
  orderedAt?: string | null;
  marketplace?: string;
};

export function OrderManagerPage() {
  const { t } = useI18n();
  const qc = useQueryClient();
  const [channel, setChannel] = useState("ALL");
  const [message, setMessage] = useState("");
  const [courierProvider, setCourierProvider] = useState("MANUAL");

  const orders = useQuery({
    queryKey: ["oms-orders", channel],
    queryFn: async () =>
      (await api.get(`/oms/orders`, { params: { channel } })).data.data as ChannelOrder[],
  });

  const listings = useQuery({
    queryKey: ["oms-listings"],
    queryFn: async () => (await api.get("/oms/listings")).data.data as any[],
  });

  const couriers = useQuery({
    queryKey: ["oms-couriers"],
    queryFn: async () => (await api.get("/oms/couriers")).data.data as any[],
  });

  const syncChannel = useMutation({
    mutationFn: async (kind: string) => {
      const ch = channel === "ALL" ? "FLIPKART" : channel;
      return (await api.post(`/admin/oms/${ch}/sync/${kind}`)).data.data;
    },
    onSuccess: async (result: any) => {
      setMessage(`${result.syncType}: ${result.status} — ${result.message || ""}`);
      await qc.invalidateQueries({ queryKey: ["oms-orders"] });
    },
  });

  const createCourier = useMutation({
    mutationFn: async (orderId: string) =>
      (await api.post(`/oms/orders/${orderId}/courier`, { provider: courierProvider })).data.data,
    onSuccess: async () => {
      setMessage(t("oms.courierCreated"));
      await qc.invalidateQueries({ queryKey: ["oms-orders"] });
      await qc.invalidateQueries({ queryKey: ["oms-couriers"] });
    },
  });

  const rows = useMemo(() => orders.data || [], [orders.data]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">{t("oms.title")}</h1>
        <p className="text-slate-500">{t("oms.subtitle")}</p>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        {["ALL", "FLIPKART", "AMAZON", "MEESHO"].map((c) => (
          <button
            key={c}
            type="button"
            onClick={() => setChannel(c)}
            className={`rounded-xl px-3 py-2 text-sm font-semibold ${
              channel === c ? "bg-teal-700 text-white" : "border border-slate-300 text-slate-700"
            }`}
          >
            {c}
          </button>
        ))}
        <button
          type="button"
          disabled={syncChannel.isPending || channel === "ALL"}
          onClick={() => syncChannel.mutate("orders")}
          className="rounded-xl border border-teal-700 px-3 py-2 text-sm font-semibold text-teal-800 disabled:opacity-50"
        >
          {t("oms.syncOrders")}
        </button>
        <button
          type="button"
          disabled={syncChannel.isPending || channel === "ALL"}
          onClick={() => syncChannel.mutate("stock")}
          className="rounded-xl border border-teal-700 px-3 py-2 text-sm font-semibold text-teal-800 disabled:opacity-50"
        >
          {t("oms.pushStock")}
        </button>
        <button
          type="button"
          disabled={syncChannel.isPending || channel === "ALL"}
          onClick={() => syncChannel.mutate("labels")}
          className="rounded-xl border border-teal-700 px-3 py-2 text-sm font-semibold text-teal-800 disabled:opacity-50"
        >
          {t("oms.fetchLabels")}
        </button>
        <select
          value={courierProvider}
          onChange={(e) => setCourierProvider(e.target.value)}
          className="rounded-xl border px-3 py-2 text-sm"
        >
          <option value="MANUAL">{t("oms.courierManual")}</option>
          <option value="SHIPROCKET">{t("oms.courierShiprocket")}</option>
          <option value="DELHIVERY">{t("oms.courierDelhivery")}</option>
          <option value="MARKETPLACE">{t("oms.courierMarketplace")}</option>
        </select>
      </div>

      {message && <p className="text-sm text-teal-800">{message}</p>}
      {(orders.isError || syncChannel.isError || createCourier.isError) && (
        <p className="text-sm text-red-600">
          {apiErrorMessage(orders.error || syncChannel.error || createCourier.error)}
        </p>
      )}

      <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
        <table className="min-w-full text-left text-sm">
          <thead className="bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="px-3 py-2">{t("oms.channel")}</th>
              <th className="px-3 py-2">{t("oms.order")}</th>
              <th className="px-3 py-2">{t("common.sku")}</th>
              <th className="px-3 py-2">{t("common.qty")}</th>
              <th className="px-3 py-2">{t("common.status")}</th>
              <th className="px-3 py-2">{t("oms.label")}</th>
              <th className="px-3 py-2">{t("oms.awb")}</th>
              <th className="px-3 py-2">{t("oms.courier")}</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td colSpan={8} className="px-3 py-6 text-slate-500">
                  {t("oms.empty")}
                </td>
              </tr>
            )}
            {rows.map((o) => (
              <tr key={o.id} className="border-t border-slate-100">
                <td className="px-3 py-2">{o.marketplace || "—"}</td>
                <td className="px-3 py-2 font-medium">{o.externalOrderId}</td>
                <td className="px-3 py-2">{o.sku || "—"}</td>
                <td className="px-3 py-2">{o.quantity}</td>
                <td className="px-3 py-2">{o.status}</td>
                <td className="px-3 py-2">
                  {o.labelUrl ? (
                    <a className="text-teal-700 underline" href={o.labelUrl} target="_blank" rel="noreferrer">
                      {o.labelStatus}
                    </a>
                  ) : (
                    o.labelStatus
                  )}
                </td>
                <td className="px-3 py-2">{o.awb || "—"}</td>
                <td className="px-3 py-2">
                  <button
                    type="button"
                    className="rounded-lg border px-2 py-1 text-xs font-semibold"
                    onClick={() => createCourier.mutate(o.id)}
                  >
                    {t("oms.create")}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <section className="grid gap-4 md:grid-cols-2">
        <div className="rounded-2xl border border-slate-200 bg-white p-4">
          <h2 className="font-semibold">{t("oms.listingsSync")}</h2>
          <p className="mt-1 text-xs text-slate-500">
            {(listings.data || []).length} {t("oms.listingRows")}
          </p>
          <ul className="mt-3 space-y-2 text-sm">
            {(listings.data || []).slice(0, 10).map((l: any) => (
              <li key={l.id} className="flex justify-between gap-2 border-b border-slate-100 py-1">
                <span>
                  {l.marketplace} · {l.sku}
                </span>
                <span className="text-slate-500">
                  {l.status} · {t("oms.stock")} {l.stockMirror ?? "—"}
                </span>
              </li>
            ))}
          </ul>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4">
          <h2 className="font-semibold">{t("oms.courierShipments")}</h2>
          <ul className="mt-3 space-y-2 text-sm">
            {(couriers.data || []).slice(0, 10).map((c: any) => (
              <li key={c.id} className="flex justify-between gap-2 border-b border-slate-100 py-1">
                <span>
                  {c.provider} · {c.awb || "—"}
                </span>
                <span className="text-slate-500">{c.status}</span>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </div>
  );
}
