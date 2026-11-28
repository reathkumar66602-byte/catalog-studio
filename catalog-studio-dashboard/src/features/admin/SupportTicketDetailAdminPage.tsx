import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Paperclip } from "lucide-react";
import { apiErrorMessage } from "../../api/client";
import { useAuth } from "../../store/auth";
import { currentNumericUserId, downloadAttachment, supportApi } from "../support/api";
import {
  formatTicketStatus,
  priorityTone,
  statusTone,
  TICKET_PRIORITIES,
  TICKET_STATUSES,
} from "../support/types";

export function SupportTicketDetailAdminPage() {
  const { id = "" } = useParams();
  const { user } = useAuth();
  const qc = useQueryClient();
  const [reply, setReply] = useState("");
  const [internal, setInternal] = useState(false);
  const [replyStatus, setReplyStatus] = useState("");
  const [closeSummary, setCloseSummary] = useState("");
  const [message, setMessage] = useState("");

  const ticket = useQuery({
    queryKey: ["support-admin-ticket", id],
    queryFn: () => supportApi.adminTicket(id),
    enabled: Boolean(id),
  });

  async function refresh() {
    await qc.invalidateQueries({ queryKey: ["support-admin-ticket", id] });
    await qc.invalidateQueries({ queryKey: ["support-admin-tickets"] });
    await qc.invalidateQueries({ queryKey: ["support-admin-stats"] });
  }

  const assign = useMutation({
    mutationFn: () =>
      supportApi.assignTicket(id, currentNumericUserId() ?? undefined, user?.name || user?.email),
    onSuccess: async () => {
      setMessage("Assigned to you.");
      await refresh();
    },
  });

  const setPriority = useMutation({
    mutationFn: (priority: string) => supportApi.updatePriority(id, priority),
    onSuccess: async () => {
      setMessage("Priority updated.");
      await refresh();
    },
  });

  const setStatus = useMutation({
    mutationFn: (status: string) => supportApi.updateStatus(id, status),
    onSuccess: async () => {
      setMessage("Status updated.");
      await refresh();
    },
  });

  const replyMut = useMutation({
    mutationFn: () =>
      supportApi.adminReply(id, {
        body: reply.trim(),
        internal,
        status: replyStatus || undefined,
      }),
    onSuccess: async () => {
      setReply("");
      setInternal(false);
      setReplyStatus("");
      setMessage(internal ? "Internal note saved." : "Reply sent.");
      await refresh();
    },
  });

  const close = useMutation({
    mutationFn: () => supportApi.closeTicket(id, closeSummary.trim() || undefined),
    onSuccess: async () => {
      setMessage("Ticket closed.");
      await refresh();
    },
  });

  if (ticket.isLoading) {
    return <p className="text-sm text-slate-500">Loading ticket…</p>;
  }
  if (ticket.isError || !ticket.data) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-red-600">{apiErrorMessage(ticket.error, "Could not load ticket")}</p>
        <Link to="/admin/support-tickets" className="text-sm text-teal-800 hover:underline">
          Back to support tickets
        </Link>
      </div>
    );
  }

  const data = ticket.data;
  const actionError = assign.error || setPriority.error || setStatus.error || replyMut.error || close.error;

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div>
        <Link to="/admin/support-tickets" className="text-sm text-teal-800 hover:underline">
          ← Support tickets
        </Link>
        <div className="mt-2 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold">{data.ticketNumber}</h1>
            <p className="mt-1 text-slate-700">{data.subject}</p>
          </div>
          <div className="flex flex-wrap gap-2 text-xs">
            <span className={`rounded-full px-2 py-0.5 ${statusTone(data.status)}`}>
              {formatTicketStatus(data.status)}
            </span>
            <span className={`rounded-full px-2 py-0.5 ${priorityTone(data.priority)}`}>
              {data.priority}
            </span>
            {data.slaBreached && (
              <span className="rounded-full bg-rose-100 px-2 py-0.5 font-medium text-rose-800">SLA breached</span>
            )}
          </div>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <section className="rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="font-medium">Customer</h2>
          <dl className="mt-3 space-y-2 text-sm">
            <div>
              <dt className="text-slate-500">Name</dt>
              <dd className="font-medium">{data.customerName || "—"}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Email</dt>
              <dd>{data.customerEmail || "—"}</dd>
            </div>
            <div>
              <dt className="text-slate-500">User id</dt>
              <dd className="font-mono text-xs">{data.customerUserId ?? "—"}</dd>
            </div>
          </dl>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="font-medium">Issue info</h2>
          <dl className="mt-3 grid gap-2 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-slate-500">Category</dt>
              <dd>{data.categoryName || data.categoryCode || "—"}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Locale</dt>
              <dd>{data.locale || "—"}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Assignee</dt>
              <dd>{data.assignedAdminName || "Unassigned"}</dd>
            </div>
            <div>
              <dt className="text-slate-500">SLA deadline</dt>
              <dd>
                {data.slaDeadlineAt ? new Date(data.slaDeadlineAt).toLocaleString() : "—"}
              </dd>
            </div>
            <div>
              <dt className="text-slate-500">Created</dt>
              <dd>{data.createdAt ? new Date(data.createdAt).toLocaleString() : "—"}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Updated</dt>
              <dd>{data.updatedAt ? new Date(data.updatedAt).toLocaleString() : "—"}</dd>
            </div>
          </dl>
        </section>
      </div>

      <section className="rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="font-medium">Description</h2>
        <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">{data.description}</p>
        {data.resolutionSummary && (
          <div className="mt-4 rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-950">
            <p className="text-xs font-medium uppercase tracking-wide text-emerald-700">Resolution</p>
            <p className="mt-1 whitespace-pre-wrap">{data.resolutionSummary}</p>
          </div>
        )}
      </section>

      {(data.attachments?.length || 0) > 0 && (
        <section className="rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="font-medium">Attachments</h2>
          <ul className="mt-3 space-y-2">
            {data.attachments.map((att) => (
              <li key={att.uuid}>
                <button
                  type="button"
                  onClick={() => downloadAttachment(att.uuid, att.originalName)}
                  className="inline-flex items-center gap-2 text-sm text-teal-800 hover:underline"
                >
                  <Paperclip size={14} />
                  {att.originalName}
                  <span className="text-xs text-slate-400">
                    ({Math.max(1, Math.round(att.sizeBytes / 1024))} KB)
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="flex flex-wrap gap-3 rounded-2xl border border-slate-200 bg-white p-5">
        <button
          type="button"
          disabled={assign.isPending}
          onClick={() => assign.mutate()}
          className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
        >
          {assign.isPending ? "Assigning…" : "Assign to me"}
        </button>
        <label className="text-sm">
          <span className="sr-only">Priority</span>
          <select
            value={data.priority}
            disabled={setPriority.isPending}
            onChange={(e) => setPriority.mutate(e.target.value)}
            className="rounded-xl border border-slate-200 px-3 py-2"
          >
            {TICKET_PRIORITIES.map((p) => (
              <option key={p} value={p}>
                Priority: {p}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="sr-only">Status</span>
          <select
            value={data.status}
            disabled={setStatus.isPending}
            onChange={(e) => setStatus.mutate(e.target.value)}
            className="rounded-xl border border-slate-200 px-3 py-2"
          >
            {TICKET_STATUSES.map((s) => (
              <option key={s} value={s}>
                Status: {formatTicketStatus(s)}
              </option>
            ))}
          </select>
        </label>
      </section>

      {(data.activities?.length || 0) > 0 && (
        <section className="rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="font-medium">Activity</h2>
          <ol className="mt-4 space-y-3 border-l border-slate-200 pl-4">
            {data.activities!.map((item, index) => (
              <li key={`${item.action}-${item.createdAt}-${index}`} className="relative text-sm">
                <span className="absolute -left-[1.35rem] top-1.5 h-2.5 w-2.5 rounded-full bg-teal-600" />
                <p className="font-medium text-slate-800">
                  {item.action.replaceAll("_", " ")}
                  {item.fromValue || item.toValue
                    ? `: ${item.fromValue || "—"} → ${item.toValue || "—"}`
                    : ""}
                </p>
                <p className="text-xs text-slate-500">
                  {item.actorName || "System"}
                  {item.createdAt ? ` · ${new Date(item.createdAt).toLocaleString()}` : ""}
                  {item.metadata ? ` · ${item.metadata}` : ""}
                </p>
              </li>
            ))}
          </ol>
        </section>
      )}

      <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="font-medium">Conversation</h2>
        {(data.messages || []).length === 0 && (
          <p className="text-sm text-slate-500">No messages yet.</p>
        )}
        {(data.messages || []).map((msg) => (
          <div
            key={msg.uuid}
            className={`rounded-xl px-3 py-2.5 ${
              msg.internal ? "border border-amber-200 bg-amber-50" : "bg-slate-50"
            }`}
          >
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
              <span className="font-medium text-slate-700">
                {msg.authorName || msg.authorRole || "Staff"}
                {msg.internal && (
                  <span className="ml-2 rounded bg-amber-200 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-amber-900">
                    Internal
                  </span>
                )}
              </span>
              <span>{msg.createdAt ? new Date(msg.createdAt).toLocaleString() : ""}</span>
            </div>
            <p className="mt-1 whitespace-pre-wrap text-sm text-slate-800">{msg.body}</p>
            {(msg.attachments || []).map((att) => (
              <button
                key={att.uuid}
                type="button"
                onClick={() => downloadAttachment(att.uuid, att.originalName)}
                className="mt-2 inline-flex items-center gap-1 text-xs text-teal-800 hover:underline"
              >
                <Paperclip size={12} />
                {att.originalName}
              </button>
            ))}
          </div>
        ))}
      </section>

      {message && <p className="text-sm text-teal-800">{message}</p>}
      {actionError && <p className="text-sm text-red-600">{apiErrorMessage(actionError)}</p>}

      <form
        className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5"
        onSubmit={(e) => {
          e.preventDefault();
          if (!reply.trim()) return;
          replyMut.mutate();
        }}
      >
        <h2 className="font-medium">Reply / note</h2>
        <textarea
          value={reply}
          onChange={(e) => setReply(e.target.value)}
          className="min-h-[110px] w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
          placeholder="Write a customer reply or an internal note…"
          required
        />
        <div className="flex flex-wrap items-center gap-4">
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={internal}
              onChange={(e) => setInternal(e.target.checked)}
            />
            Internal note (not visible to customer)
          </label>
          <label className="text-sm">
            <span className="mr-2 text-slate-500">Set status</span>
            <select
              value={replyStatus}
              onChange={(e) => setReplyStatus(e.target.value)}
              className="rounded-xl border border-slate-200 px-3 py-1.5"
            >
              <option value="">No change</option>
              {TICKET_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {formatTicketStatus(s)}
                </option>
              ))}
            </select>
          </label>
        </div>
        <button
          type="submit"
          disabled={replyMut.isPending}
          className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
        >
          {replyMut.isPending ? "Saving…" : internal ? "Save note" : "Send reply"}
        </button>
      </form>

      {String(data.status).toUpperCase() !== "CLOSED" && (
        <section className="space-y-3 rounded-2xl border border-rose-200 bg-rose-50/40 p-5">
          <h2 className="font-medium text-rose-950">Close ticket</h2>
          <textarea
            value={closeSummary}
            onChange={(e) => setCloseSummary(e.target.value)}
            className="min-h-[80px] w-full rounded-xl border border-rose-200 bg-white px-3 py-2 text-sm"
            placeholder="Optional resolution summary for the customer…"
          />
          <button
            type="button"
            disabled={close.isPending}
            onClick={() => close.mutate()}
            className="rounded-xl border border-rose-300 bg-white px-4 py-2 text-sm font-medium text-rose-800 disabled:opacity-60"
          >
            {close.isPending ? "Closing…" : "Close ticket"}
          </button>
        </section>
      )}
    </div>
  );
}
