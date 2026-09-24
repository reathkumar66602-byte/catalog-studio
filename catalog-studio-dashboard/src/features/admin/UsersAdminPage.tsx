import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, apiErrorMessage } from "../../api/client";
import { AdminDialog } from "./AdminDialog";
import { AdminTableBar, SortHeader, StatusBadge } from "./AdminTableBar";
import type { AdminUserRow, PageResult, PlanOption } from "./adminTypes";

type PendingAction = {
  row: AdminUserRow;
  action: "ACTIVATE" | "DEACTIVATE" | "CHANGE_PLAN";
  plan: string;
  reference: string;
};

export function UsersAdminPage() {
  const qc = useQueryClient();
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState("createdAt,desc");
  const [drafts, setDrafts] = useState<Record<string, { plan: string; reference: string }>>({});
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState<PendingAction | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebounced(query.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  const list = useQuery({
    queryKey: ["admin-users", debounced, page, sort],
    queryFn: async () =>
      (await api.get("/admin/workspace/users", { params: { q: debounced || undefined, page, size: 20, sort } }))
        .data.data as PageResult<AdminUserRow>,
  });
  const plans = useQuery({
    queryKey: ["plans"],
    queryFn: async () => (await api.get("/subscriptions/plans")).data.data as PlanOption[],
  });

  const paidPlans = useMemo(
    () => (plans.data || []).filter((plan) => plan.name !== "FREE"),
    [plans.data],
  );

  const action = useMutation({
    mutationFn: (payload: { id: string; action: string; planName: string; reference?: string }) =>
      api.put(`/admin/workspace/users/${payload.id}/subscription`, {
        action: payload.action,
        planName: payload.planName,
        reference: payload.reference,
      }),
    onSuccess: async (_res, variables) => {
      setPending(null);
      setMessage(
        variables.action === "ACTIVATE"
          ? "Plan activated. The seller workspace is open and a WhatsApp payment was added to Transactions."
          : variables.action === "DEACTIVATE"
            ? "Workspace locked. This seller will be asked to recharge."
            : "Plan switched. Access dates are unchanged and a plan-change record was added to Transactions.",
      );
      await qc.invalidateQueries({ queryKey: ["admin-users"] });
    },
  });

  const rows = list.data?.content || [];

  function draftFor(row: AdminUserRow) {
    return drafts[row.id] || { plan: row.plan && row.plan !== "NONE" ? row.plan : paidPlans[0]?.name || "BASIC", reference: "" };
  }

  function openAction(row: AdminUserRow, nextAction: PendingAction["action"]) {
    const draft = draftFor(row);
    setMessage("");
    setPending({ row, action: nextAction, plan: draft.plan, reference: draft.reference });
  }

  function confirmPending() {
    if (!pending) return;
    const apiAction = pending.action === "CHANGE_PLAN" && !pending.row.accessEntitled ? "ACTIVATE" : pending.action;
    action.mutate({
      id: pending.row.id,
      action: apiAction,
      planName: pending.plan,
      reference: pending.reference || undefined,
    });
  }

  const dialog = dialogCopy(pending);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Users</h1>
        <p className="mt-1 text-sm text-slate-500">
          After a seller sends the UPI screenshot on WhatsApp, select the plan and click Activate. That unlocks the
          workspace for one billing period and writes a Transactions row. Change plan is only for sellers who already
          have active access — it switches the plan without a new payment and does not extend dates. If access has
          ended, Change plan stays off until you Activate. Deactivate locks tools until the next payment.
        </p>
      </div>
      <AdminTableBar
        query={query}
        onQuery={setQuery}
        page={page}
        totalPages={list.data?.totalPages || 0}
        totalElements={list.data?.totalElements || 0}
        onPage={setPage}
        noun="users"
      />
      {message && <p className="text-sm text-teal-800">{message}</p>}
      {action.isError && <p className="text-sm text-red-600">{apiErrorMessage(action.error)}</p>}
      <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
        <table className="w-full min-w-[1100px] text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="p-3">
                <SortHeader label="User" field="name" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th>
                <SortHeader label="Email" field="email" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th>Phone</th>
              <th>Plan</th>
              <th>
                <SortHeader label="Status" field="status" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th>Access</th>
              <th>WhatsApp ref</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {list.isLoading && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={8}>Loading users...</td>
              </tr>
            )}
            {list.isError && (
              <tr>
                <td className="p-6 text-red-600" colSpan={8}>{apiErrorMessage(list.error, "Could not load users")}</td>
              </tr>
            )}
            {!list.isLoading && !list.isError && rows.length === 0 && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={8}>No sellers match this search.</td>
              </tr>
            )}
            {rows.map((row) => {
              const draft = draftFor(row);
              return (
                <tr key={row.id} className="border-t align-top">
                  <td className="p-3">
                    <p className="font-medium">{row.name}</p>
                    <p className="text-xs text-slate-500">{row.displayRole}</p>
                  </td>
                  <td className="p-3">{row.email}</td>
                  <td className="p-3">{row.mobile || "—"}</td>
                  <td className="p-3">
                    <select
                      value={draft.plan}
                      onChange={(event) =>
                        setDrafts((current) => ({ ...current, [row.id]: { ...draft, plan: event.target.value } }))
                      }
                      className="rounded-lg border px-2 py-1.5"
                    >
                      {paidPlans.map((plan) => (
                        <option key={plan.name} value={plan.name}>
                          {plan.name} · ₹{Number(plan.price || 0).toLocaleString("en-IN")}
                        </option>
                      ))}
                    </select>
                    <p className="mt-1 text-xs text-slate-500">{planHint(row)}</p>
                  </td>
                  <td className="p-3">{row.status}</td>
                  <td className="p-3">
                    <StatusBadge entitled={row.accessEntitled} status={row.planStatus} />
                  </td>
                  <td className="p-3">
                    <input
                      value={draft.reference}
                      onChange={(event) =>
                        setDrafts((current) => ({ ...current, [row.id]: { ...draft, reference: event.target.value } }))
                      }
                      placeholder="UTR / UPI ref"
                      className="w-36 rounded-lg border px-2 py-1.5 text-xs"
                    />
                  </td>
                  <td className="p-3">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        disabled={action.isPending}
                        onClick={() => openAction(row, "ACTIVATE")}
                        className="rounded-lg bg-teal-700 px-3 py-1.5 text-white"
                      >
                        Activate
                      </button>
                      <button
                        type="button"
                        disabled={action.isPending || !row.accessEntitled}
                        title={
                          row.accessEntitled
                            ? "Switch the paid plan without collecting another payment"
                            : "Access has ended. Use Activate after the WhatsApp payment."
                        }
                        onClick={() => openAction(row, "CHANGE_PLAN")}
                        className="rounded-lg border px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        Change plan
                      </button>
                      <button
                        type="button"
                        disabled={action.isPending}
                        onClick={() => openAction(row, "DEACTIVATE")}
                        className="rounded-lg border border-rose-200 px-3 py-1.5 text-rose-700"
                      >
                        Deactivate
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {pending && dialog && (
        <AdminDialog
          title={dialog.title}
          confirmLabel={dialog.confirmLabel}
          danger={dialog.danger}
          pending={action.isPending}
          onConfirm={dialog.infoOnly ? () => setPending(null) : confirmPending}
          onClose={() => setPending(null)}
          cancelLabel={dialog.infoOnly ? "Close" : "Cancel"}
        >
          {dialog.body.map((line) => (
            <p key={line}>{line}</p>
          ))}
        </AdminDialog>
      )}
    </div>
  );
}

function planHint(row: AdminUserRow) {
  const plan = row.plan || "—";
  if (!row.subscriptionEndDate) return `Current ${plan}`;
  if (row.accessEntitled) return `Current ${plan} · access until ${row.subscriptionEndDate}`;
  return `Current ${plan} · access ended ${row.subscriptionEndDate}`;
}

function dialogCopy(pending: PendingAction | null) {
  if (!pending) return null;
  const { row, action, plan } = pending;
  if (action === "ACTIVATE") {
    return {
      title: "Activate paid plan",
      confirmLabel: `Activate ${plan}`,
      danger: false,
      infoOnly: false,
      body: [
        `Confirm that ${row.email} sent the UPI screenshot on WhatsApp.`,
        `Catalog Studio will set the plan to ${plan}, unlock the workspace for one billing period, and add a paid entry to Transactions.`,
      ],
    };
  }
  if (action === "DEACTIVATE") {
    return {
      title: "Lock this workspace",
      confirmLabel: "Deactivate access",
      danger: true,
      infoOnly: false,
      body: [
        `${row.email} will be asked to recharge. Listings stay in the account. Tools stay locked until you activate a paid plan.`,
      ],
    };
  }
  if (!row.accessEntitled) {
    return {
      title: "Access has ended",
      confirmLabel: `Activate ${plan}`,
      danger: false,
      infoOnly: false,
      body: [
        `${row.email} is on ${row.plan || "no plan"}, and access ${row.subscriptionEndDate ? `ended on ${row.subscriptionEndDate}` : "is not active"}.`,
        "Changing the plan does not reopen the workspace. After you confirm the WhatsApp payment, activate the selected plan.",
      ],
    };
  }
  if ((row.plan || "").toUpperCase() === plan.toUpperCase()) {
    return {
      title: "Plan already active",
      confirmLabel: "OK",
      danger: false,
      infoOnly: true,
      body: [
        `${row.email} is already on ${plan}${row.subscriptionEndDate ? ` until ${row.subscriptionEndDate}` : ""}. Select a different plan to switch.`,
      ],
    };
  }
  return {
    title: "Switch plan",
    confirmLabel: `Switch to ${plan}`,
    danger: false,
    infoOnly: false,
    body: [
      `Switch ${row.email} from ${row.plan} to ${plan}.`,
      `Access continues until ${row.subscriptionEndDate || "the current period ends"}. This does not collect a new payment. A plan-change record is added to Transactions.`,
    ],
  };
}
