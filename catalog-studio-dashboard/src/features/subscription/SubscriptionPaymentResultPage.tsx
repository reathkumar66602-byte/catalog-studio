import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import { api, apiErrorMessage } from "../../api/client";

type PaymentStatus = {
  linkId: string;
  provider: string;
  status: string;
  plan: string;
  amount: number;
  accessEntitled: boolean;
  message: string;
};

function inr(value?: number | null) {
  return `₹${Number(value || 0).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function SubscriptionPaymentResultPage() {
  const [params] = useSearchParams();
  const orderId = params.get("order_id") || params.get("link_id") || "";

  const status = useQuery({
    queryKey: ["payment-status", orderId],
    enabled: Boolean(orderId),
    refetchInterval: (query) => {
      const data = query.state.data as PaymentStatus | undefined;
      if (!data) return 2500;
      if (data.accessEntitled || data.status === "SUCCESS" || data.status === "FAILED" || data.status === "EXPIRED") {
        return false;
      }
      return 2500;
    },
    queryFn: async () =>
      (await api.get(`/subscriptions/payments/${encodeURIComponent(orderId)}`)).data.data as PaymentStatus,
  });

  if (!orderId) {
    return (
      <div className="mx-auto max-w-lg space-y-4 rounded-2xl border border-slate-200 bg-white p-8 text-center">
        <p className="text-slate-600">Missing payment reference.</p>
        <Link to="/subscription" className="font-medium text-teal-700 hover:underline">
          Back to plans
        </Link>
      </div>
    );
  }

  const data = status.data;
  const done = Boolean(data?.accessEntitled || data?.status === "SUCCESS");
  const failed = data?.status === "FAILED" || data?.status === "EXPIRED";

  return (
    <div className="mx-auto max-w-lg space-y-6 rounded-2xl border border-slate-200 bg-white p-8 text-center">
      {status.isLoading && !data ? (
        <>
          <Loader2 className="mx-auto h-10 w-10 animate-spin text-teal-700" />
          <h1 className="text-xl font-semibold">Confirming payment…</h1>
          <p className="text-sm text-slate-500">Cashfree is verifying your payment.</p>
        </>
      ) : done ? (
        <>
          <CheckCircle2 className="mx-auto h-12 w-12 text-emerald-600" />
          <h1 className="text-xl font-semibold text-emerald-900">Payment successful</h1>
          <p className="text-sm text-slate-600">{data?.message || "Your plan is active."}</p>
          {data?.plan && (
            <p className="text-sm font-medium text-slate-800">
              {data.plan} · {inr(data.amount)}
            </p>
          )}
          <div className="flex flex-wrap items-center justify-center gap-3">
            <Link
              to="/dashboard"
              className="inline-flex rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-800"
            >
              Go to dashboard
            </Link>
            <Link to="/transactions" className="inline-flex rounded-xl border px-4 py-2.5 text-sm font-medium">
              View invoice
            </Link>
          </div>
        </>
      ) : failed ? (
        <>
          <XCircle className="mx-auto h-12 w-12 text-rose-600" />
          <h1 className="text-xl font-semibold text-rose-900">Payment not completed</h1>
          <p className="text-sm text-slate-600">{data?.message}</p>
          <Link to="/subscription" className="inline-flex rounded-xl border px-4 py-2.5 text-sm font-medium">
            Try again
          </Link>
        </>
      ) : (
        <>
          <Loader2 className="mx-auto h-10 w-10 animate-spin text-teal-700" />
          <h1 className="text-xl font-semibold">Waiting for confirmation…</h1>
          <p className="text-sm text-slate-500">
            {data?.message || "This usually takes a few seconds after you finish on Cashfree."}
          </p>
          {status.isError && (
            <p className="text-sm text-red-600">{apiErrorMessage(status.error, "Could not check payment status")}</p>
          )}
        </>
      )}
    </div>
  );
}
