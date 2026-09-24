import type { ReactNode } from "react";

export function AdminDialog({
  title,
  children,
  confirmLabel,
  cancelLabel = "Cancel",
  danger = false,
  pending = false,
  onConfirm,
  onClose,
}: {
  title: string;
  children: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  danger?: boolean;
  pending?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/40 p-4" role="dialog" aria-modal="true">
      <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
        <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
        <div className="mt-3 space-y-2 text-sm leading-6 text-slate-600">{children}</div>
        <div className="mt-6 flex justify-end gap-2">
          <button
            type="button"
            disabled={pending}
            onClick={onClose}
            className="rounded-xl border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 disabled:opacity-50"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            disabled={pending}
            onClick={onConfirm}
            className={`rounded-xl px-4 py-2 text-sm font-medium text-white disabled:opacity-50 ${
              danger ? "bg-rose-700" : "bg-teal-700"
            }`}
          >
            {pending ? "Please wait..." : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
