import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Paperclip } from "lucide-react";
import { apiErrorMessage } from "../../api/client";
import { downloadAttachment, supportApi } from "./api";
import {
  formatTicketStatus,
  priorityTone,
  statusTone,
} from "./types";

export function TicketDetailPage() {
  const { id = "" } = useParams();
  const qc = useQueryClient();
  const [reply, setReply] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const [message, setMessage] = useState("");

  const ticket = useQuery({
    queryKey: ["support-ticket", id],
    queryFn: () => supportApi.getTicket(id),
    enabled: Boolean(id),
  });

  const replyMut = useMutation({
    mutationFn: async () => {
      const attachmentIds: string[] = [];
      for (const file of files) {
        const uploaded = await supportApi.uploadPendingAttachment(file);
        attachmentIds.push(uploaded.uuid);
      }
      return supportApi.replyTicket(id, reply.trim(), attachmentIds.length ? attachmentIds : undefined);
    },
    onSuccess: async () => {
      setReply("");
      setFiles([]);
      setMessage("Reply sent.");
      await qc.invalidateQueries({ queryKey: ["support-ticket", id] });
      await qc.invalidateQueries({ queryKey: ["support-tickets"] });
    },
  });

  const reopen = useMutation({
    mutationFn: () => supportApi.reopenTicket(id),
    onSuccess: async () => {
      setMessage("Ticket reopened.");
      await qc.invalidateQueries({ queryKey: ["support-ticket", id] });
      await qc.invalidateQueries({ queryKey: ["support-tickets"] });
    },
  });

  const uploadExtra = useMutation({
    mutationFn: (file: File) => supportApi.uploadTicketAttachment(id, file),
    onSuccess: async () => {
      setMessage("Attachment uploaded.");
      await qc.invalidateQueries({ queryKey: ["support-ticket", id] });
    },
  });

  if (ticket.isLoading) {
    return <p className="text-sm text-slate-500">Loading ticket…</p>;
  }
  if (ticket.isError || !ticket.data) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-red-600">{apiErrorMessage(ticket.error, "Could not load ticket")}</p>
        <Link to="/support/tickets" className="text-sm text-teal-800 hover:underline">
          Back to my tickets
        </Link>
      </div>
    );
  }

  const data = ticket.data;
  const closedLike = ["CLOSED", "RESOLVED"].includes(String(data.status).toUpperCase());

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <Link to="/support/tickets" className="text-sm text-teal-800 hover:underline">
          ← My tickets
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">{data.ticketNumber}</h1>
        <p className="mt-1 text-slate-700">{data.subject}</p>
        <div className="mt-3 flex flex-wrap gap-2 text-xs">
          <span className={`rounded-full px-2 py-0.5 ${statusTone(data.status)}`}>
            {formatTicketStatus(data.status)}
          </span>
          <span className={`rounded-full px-2 py-0.5 ${priorityTone(data.priority)}`}>
            {data.priority}
          </span>
          {data.categoryName && (
            <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-700">{data.categoryName}</span>
          )}
        </div>
      </div>

      <section className="rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="font-medium">Issue</h2>
        <p className="mt-2 whitespace-pre-wrap text-sm text-slate-700">{data.description}</p>
        <dl className="mt-4 grid gap-2 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-slate-500">Created</dt>
            <dd>{data.createdAt ? new Date(data.createdAt).toLocaleString() : "—"}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Updated</dt>
            <dd>{data.updatedAt ? new Date(data.updatedAt).toLocaleString() : "—"}</dd>
          </div>
          {data.assignedAdminName && (
            <div>
              <dt className="text-slate-500">Assigned to</dt>
              <dd>{data.assignedAdminName}</dd>
            </div>
          )}
          {data.resolutionSummary && (
            <div className="sm:col-span-2">
              <dt className="text-slate-500">Resolution</dt>
              <dd className="whitespace-pre-wrap">{data.resolutionSummary}</dd>
            </div>
          )}
        </dl>
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
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {(data.activities || []).filter((a) =>
        ["STATUS_CHANGED", "ASSIGNED", "PRIORITY_CHANGED", "CLOSED", "REOPENED", "CREATED"].includes(
          String(a.action).toUpperCase(),
        ),
      ).length > 0 && (
        <section className="rounded-2xl border border-slate-100 bg-slate-50/80 p-4">
          <h2 className="text-sm font-medium text-slate-700">Status updates</h2>
          <ul className="mt-2 space-y-1.5 text-xs text-slate-600">
            {(data.activities || [])
              .filter((a) =>
                ["STATUS_CHANGED", "ASSIGNED", "PRIORITY_CHANGED", "CLOSED", "REOPENED", "CREATED"].includes(
                  String(a.action).toUpperCase(),
                ),
              )
              .map((item, index) => (
                <li key={`${item.action}-${item.createdAt}-${index}`}>
                  <span className="font-medium text-slate-700">
                    {item.action.replaceAll("_", " ")}
                  </span>
                  {item.fromValue || item.toValue
                    ? ` · ${item.fromValue || "—"} → ${item.toValue || "—"}`
                    : ""}
                  {item.createdAt ? ` · ${new Date(item.createdAt).toLocaleString()}` : ""}
                </li>
              ))}
          </ul>
        </section>
      )}

      <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="font-medium">Conversation</h2>
        {(data.messages || []).length === 0 && (
          <p className="text-sm text-slate-500">No replies yet.</p>
        )}
        {(data.messages || []).map((msg) => (
          <div key={msg.uuid} className="rounded-xl bg-slate-50 px-3 py-2.5">
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
              <span className="font-medium text-slate-700">
                {msg.authorName || msg.authorRole || "Support"}
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
      {(replyMut.isError || reopen.isError || uploadExtra.isError) && (
        <p className="text-sm text-red-600">
          {apiErrorMessage(replyMut.error || reopen.error || uploadExtra.error)}
        </p>
      )}

      {closedLike ? (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5">
          <p className="text-sm text-amber-950">This ticket is {formatTicketStatus(data.status).toLowerCase()}.</p>
          <button
            type="button"
            disabled={reopen.isPending}
            onClick={() => reopen.mutate()}
            className="mt-3 rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
          >
            {reopen.isPending ? "Reopening…" : "Reopen ticket"}
          </button>
        </div>
      ) : (
        <form
          className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5"
          onSubmit={(e) => {
            e.preventDefault();
            if (!reply.trim()) return;
            replyMut.mutate();
          }}
        >
          <h2 className="font-medium">Reply</h2>
          <textarea
            value={reply}
            onChange={(e) => setReply(e.target.value)}
            className="min-h-[100px] w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
            placeholder="Add more details for the support team…"
            required
          />
          <input
            type="file"
            multiple
            accept="image/*,application/pdf"
            onChange={(e) => setFiles(Array.from(e.target.files || []))}
            className="block w-full text-sm"
          />
          <div className="flex flex-wrap gap-2">
            <button
              type="submit"
              disabled={replyMut.isPending}
              className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
            >
              {replyMut.isPending ? "Sending…" : "Send reply"}
            </button>
            <label className="cursor-pointer rounded-xl border border-slate-200 px-4 py-2 text-sm">
              Upload to ticket
              <input
                type="file"
                accept="image/*,application/pdf"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) uploadExtra.mutate(file);
                  e.target.value = "";
                }}
              />
            </label>
          </div>
        </form>
      )}
    </div>
  );
}
