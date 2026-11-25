import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import axios from "axios";
import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";
import { useI18n } from "../../i18n/LanguageProvider";
import type { WholesaleOrder } from "./wholesale";

type ReceiptLine = {
  id: string;
  orderItemId: string;
  sku?: string | null;
  productTitle: string;
  orderedQty: number;
  receivedQty: number;
  missingQty: number;
  damagedQty: number;
  rejectedQty: number;
  acceptedQty: number;
};

type GoodsReceipt = {
  id: string;
  status: "PENDING" | "CONFIRMED" | "FAILED";
  note?: string | null;
  lastError?: string | null;
  lines: ReceiptLine[];
};

type DraftLine = {
  orderItemId: string;
  receivedQty: number;
  damagedQty: number;
  rejectedQty: number;
};

export function GoodsReceiptPanel({ order }: { order: WholesaleOrder }) {
  const { t } = useI18n();
  const qc = useQueryClient();
  const open = order.status === "ACCEPTED" || order.status === "FULFILLED";
  const [note, setNote] = useState("");
  const [draft, setDraft] = useState<DraftLine[]>(() =>
    order.items.map((item) => ({
      orderItemId: item.id,
      receivedQty: item.quantity,
      damagedQty: 0,
      rejectedQty: 0,
    })),
  );

  const receipt = useQuery({
    queryKey: ["ws-goods-receipt", order.id],
    enabled: open,
    queryFn: async () => {
      try {
        return (await api.get(`/wholesale/buyer/orders/${order.id}/goods-receipt`)).data.data as GoodsReceipt;
      } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 404) return null;
        throw error;
      }
    },
  });

  const save = useMutation({
    mutationFn: async (lines: DraftLine[]) =>
      (
        await api.post(`/wholesale/buyer/orders/${order.id}/goods-receipt`, {
          note: note.trim() || null,
          lines,
        })
      ).data.data as GoodsReceipt,
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ["ws-goods-receipt", order.id] });
      await qc.invalidateQueries({ queryKey: ["inventory"] });
    },
  });

  const preview = useMemo(
    () =>
      draft.map((line) => {
        const ordered = order.items.find((item) => item.id === line.orderItemId)?.quantity ?? 0;
        const missing = ordered - line.receivedQty;
        const accepted = line.receivedQty - line.damagedQty - line.rejectedQty;
        const invalid =
          line.receivedQty < 0 ||
          line.damagedQty < 0 ||
          line.rejectedQty < 0 ||
          line.receivedQty > ordered ||
          line.damagedQty + line.rejectedQty > line.receivedQty;
        return { ...line, ordered, missing, accepted, invalid };
      }),
    [draft, order.items],
  );

  if (!open) return null;

  const current = receipt.data;
  if (receipt.isLoading) {
    return <p className="text-xs text-slate-500">{t("buyerOrders.loading")}</p>;
  }
  if (receipt.isError) {
    return <p className="text-sm text-red-600">{apiErrorMessage(receipt.error)}</p>;
  }

  if (current?.status === "CONFIRMED") {
    return (
      <div className="space-y-2 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-3 text-sm dark:border-emerald-900 dark:bg-emerald-950/30">
        <p className="font-semibold text-emerald-900 dark:text-emerald-100">{t("buyerOrders.receiptTitle")}</p>
        <p className="text-emerald-800 dark:text-emerald-200">{t("buyerOrders.receiptConfirmed")}</p>
        <ReceiptTable lines={current.lines} />
        <Link to="/inventory" className="inline-block text-sm font-semibold text-teal-800 underline">
          {t("nav.inventory")}
        </Link>
      </div>
    );
  }

  const retryLines: DraftLine[] | null = current
    ? current.lines.map((line) => ({
        orderItemId: line.orderItemId,
        receivedQty: line.receivedQty,
        damagedQty: line.damagedQty,
        rejectedQty: line.rejectedQty,
      }))
    : null;

  return (
    <div className="space-y-3 rounded-xl border border-slate-200 px-3 py-3 dark:border-slate-700">
      <div>
        <p className="font-semibold">{t("buyerOrders.receiptTitle")}</p>
        <p className="text-xs text-slate-500">{t("buyerOrders.receiptHelp")}</p>
      </div>
      {current?.lastError && <p className="text-sm text-red-600">{current.lastError}</p>}
      {save.isError && <p className="text-sm text-red-600">{apiErrorMessage(save.error)}</p>}

      {retryLines ? (
        <button
          type="button"
          disabled={save.isPending}
          onClick={() => save.mutate(retryLines)}
          className="rounded-lg bg-teal-700 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
        >
          {t("buyerOrders.retryReceipt")}
        </button>
      ) : (
        <>
          <div className="space-y-3">
            {preview.map((line, index) => {
              const item = order.items.find((entry) => entry.id === line.orderItemId);
              return (
                <div key={line.orderItemId} className="space-y-2 rounded-lg bg-slate-50 p-2 dark:bg-slate-800">
                  <p className="text-sm font-medium">
                    {item?.productTitle}
                    <span className="ml-2 text-xs text-slate-500">
                      {t("common.qty")} {line.ordered}
                      {item?.sku ? ` · ${item.sku}` : ""}
                    </span>
                  </p>
                  <div className="grid grid-cols-3 gap-2">
                    <QtyField
                      label={t("buyerOrders.received")}
                      value={line.receivedQty}
                      onChange={(value) => updateDraft(index, "receivedQty", value)}
                    />
                    <QtyField
                      label={t("buyerOrders.damaged")}
                      value={line.damagedQty}
                      onChange={(value) => updateDraft(index, "damagedQty", value)}
                    />
                    <QtyField
                      label={t("buyerOrders.rejected")}
                      value={line.rejectedQty}
                      onChange={(value) => updateDraft(index, "rejectedQty", value)}
                    />
                  </div>
                  <p className={`text-xs ${line.invalid ? "text-red-600" : "text-slate-500"}`}>
                    {t("buyerOrders.missing")} {Math.max(0, line.missing)} · {t("buyerOrders.accepted")} {Math.max(0, line.accepted)}
                  </p>
                </div>
              );
            })}
          </div>
          <label className="block text-xs text-slate-500">
            {t("buyerOrders.receiptNote")}
            <input
              value={note}
              maxLength={1000}
              onChange={(event) => setNote(event.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-900"
            />
          </label>
          <button
            type="button"
            disabled={save.isPending || preview.some((line) => line.invalid)}
            onClick={() => {
              const accepted = preview.reduce((sum, line) => sum + line.accepted, 0);
              const ok = window.confirm(
                `${t("buyerOrders.receiptHelp")} ${t("buyerOrders.accepted")}: ${accepted}`,
              );
              if (ok) {
                save.mutate(draft);
              }
            }}
            className="rounded-lg bg-teal-700 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
          >
            {t("buyerOrders.confirmReceipt")}
          </button>
        </>
      )}
    </div>
  );

  function updateDraft(index: number, field: keyof Omit<DraftLine, "orderItemId">, value: number) {
    setDraft((currentDraft) =>
      currentDraft.map((line, lineIndex) => (lineIndex === index ? { ...line, [field]: value } : line)),
    );
  }
}

function QtyField({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return (
    <label className="text-xs text-slate-500">
      {label}
      <input
        type="number"
        min={0}
        value={value}
        onChange={(event) => onChange(Math.max(0, Number(event.target.value) || 0))}
        className="mt-1 w-full rounded-lg border border-slate-200 bg-white px-2 py-1.5 text-sm dark:border-slate-700 dark:bg-slate-900"
      />
    </label>
  );
}

function ReceiptTable({ lines }: { lines: ReceiptLine[] }) {
  const { t } = useI18n();
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[420px] text-left text-xs">
        <thead>
          <tr className="text-slate-500">
            <th className="py-1">{t("common.item")}</th>
            <th>{t("buyerOrders.received")}</th>
            <th>{t("buyerOrders.missing")}</th>
            <th>{t("buyerOrders.damaged")}</th>
            <th>{t("buyerOrders.rejected")}</th>
            <th>{t("buyerOrders.accepted")}</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((line) => (
            <tr key={line.id} className="border-t border-emerald-100 dark:border-emerald-900">
              <td className="py-1">{line.productTitle}</td>
              <td>{line.receivedQty}</td>
              <td>{line.missingQty}</td>
              <td>{line.damagedQty}</td>
              <td>{line.rejectedQty}</td>
              <td className="font-semibold">{line.acceptedQty}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
