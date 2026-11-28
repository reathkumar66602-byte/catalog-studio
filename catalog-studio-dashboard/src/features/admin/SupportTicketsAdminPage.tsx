import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { Headphones } from "lucide-react";
import { apiErrorMessage } from "../../api/client";
import { supportApi } from "../support/api";
import {
  formatTicketStatus,
  priorityTone,
  statusTone,
  TICKET_PRIORITIES,
  TICKET_STATUSES,
} from "../support/types";
import { AdminTableBar, SortHeader } from "./AdminTableBar";

export function SupportTicketsAdminPage() {
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [status, setStatus] = useState("OPEN");
  const [priority, setPriority] = useState("");
  const [category, setCategory] = useState("");
  const [slaBreached, setSlaBreached] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState("createdAt,desc");

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebounced(query.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  const stats = useQuery({
    queryKey: ["support-admin-stats"],
    queryFn: () => supportApi.adminStats(),
    staleTime: 15_000,
  });

  const categories = useQuery({
    queryKey: ["support-admin-categories"],
    queryFn: () => supportApi.adminCategories(),
    staleTime: 60_000,
  });

  const list = useQuery({
    queryKey: ["support-admin-tickets", debounced, status, priority, category, slaBreached, page, sort],
    queryFn: () =>
      supportApi.adminTickets({
        status: status || "ALL",
        priority: priority || undefined,
        category: category || undefined,
        slaBreached: slaBreached === "" ? undefined : slaBreached === "true",
        q: debounced || undefined,
        page,
        size: 20,
        sort,
      }),
  });

  const rows = list.data?.content || [];
  const s = stats.data;

  const cards = [
    { label: "Total tickets", value: s?.totalCount },
    { label: "Open", value: s?.openCount },
    { label: "Assigned", value: s?.assignedCount },
    { label: "In progress", value: s?.inProgressCount },
    { label: "Waiting", value: s?.waitingForCustomerCount },
    { label: "Unassigned", value: s?.unassignedCount },
    { label: "SLA breached", value: s?.slaBreachedCount, danger: true },
    { label: "Resolved", value: s?.resolvedCount },
    { label: "Closed", value: s?.closedCount },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-semibold">
          <Headphones className="text-teal-700" size={26} />
          Support tickets
        </h1>
        <p className="mt-1 text-sm text-slate-500">
          Triage customer tickets, watch SLA risk, and assign work to staff.{" "}
          <Link to="/admin/support-settings" className="font-medium text-teal-700 hover:underline">
            Knowledge base &amp; SLA settings
          </Link>
        </p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {cards.map((card) => (
          <div
            key={card.label}
            className={`rounded-2xl border bg-white px-4 py-3 ${
              card.danger ? "border-rose-200" : "border-slate-200"
            }`}
          >
            <p className="text-xs uppercase tracking-wide text-slate-500">{card.label}</p>
            <p className={`mt-1 text-2xl font-semibold ${card.danger ? "text-rose-700" : "text-slate-900"}`}>
              {stats.isLoading ? "…" : card.value ?? 0}
            </p>
          </div>
        ))}
      </div>
      {stats.isError && (
        <p className="text-sm text-red-600">{apiErrorMessage(stats.error, "Could not load stats")}</p>
      )}

      <div className="flex flex-wrap gap-3">
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">Status</span>
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
            className="rounded-xl border border-slate-200 px-3 py-2"
          >
            <option value="">All</option>
            {TICKET_STATUSES.map((st) => (
              <option key={st} value={st}>
                {formatTicketStatus(st)}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">Priority</span>
          <select
            value={priority}
            onChange={(e) => {
              setPriority(e.target.value);
              setPage(0);
            }}
            className="rounded-xl border border-slate-200 px-3 py-2"
          >
            <option value="">All</option>
            {TICKET_PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">Category</span>
          <select
            value={category}
            onChange={(e) => {
              setCategory(e.target.value);
              setPage(0);
            }}
            className="rounded-xl border border-slate-200 px-3 py-2"
          >
            <option value="">All</option>
            {(categories.data || []).map((c) => (
              <option key={c.uuid} value={c.code}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label className="text-sm">
          <span className="mb-1 block text-slate-500">SLA</span>
          <select
            value={slaBreached}
            onChange={(e) => {
              setSlaBreached(e.target.value);
              setPage(0);
            }}
            className="rounded-xl border border-slate-200 px-3 py-2"
          >
            <option value="">Any</option>
            <option value="true">Breached</option>
            <option value="false">On track</option>
          </select>
        </label>
      </div>

      <AdminTableBar
        query={query}
        onQuery={setQuery}
        page={page}
        totalPages={list.data?.totalPages || 0}
        totalElements={list.data?.totalElements || 0}
        onPage={setPage}
        noun="tickets"
      />

      <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
        <table className="w-full min-w-[1100px] text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="p-3">
                <SortHeader label="Ticket" field="ticketNumber" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th className="p-3">
                <SortHeader label="Subject" field="subject" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th className="p-3">Customer</th>
              <th className="p-3">Status</th>
              <th className="p-3">
                <SortHeader label="Priority" field="priority" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th className="p-3">Assignee</th>
              <th className="p-3">SLA</th>
              <th className="p-3">
                <SortHeader label="Created" field="createdAt" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
            </tr>
          </thead>
          <tbody>
            {list.isLoading && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={8}>Loading tickets…</td>
              </tr>
            )}
            {list.isError && (
              <tr>
                <td className="p-6 text-red-600" colSpan={8}>
                  {apiErrorMessage(list.error, "Could not load tickets")}
                </td>
              </tr>
            )}
            {!list.isLoading && !list.isError && rows.length === 0 && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={8}>No tickets match these filters.</td>
              </tr>
            )}
            {rows.map((row) => {
              const urgent = ["HIGH", "URGENT"].includes(String(row.priority).toUpperCase());
              const rowClass = row.slaBreached
                ? "border-t bg-rose-50/70"
                : urgent
                  ? "border-t bg-amber-50/50"
                  : "border-t hover:bg-slate-50";
              return (
                <tr key={row.uuid} className={rowClass}>
                  <td className="p-3">
                    <Link
                      to={`/admin/support-tickets/${row.uuid}`}
                      className="font-medium text-teal-800 hover:underline"
                    >
                      {row.ticketNumber}
                    </Link>
                  </td>
                  <td className="p-3">{row.subject}</td>
                  <td className="p-3">
                    <p className="font-medium">{row.customerName || "—"}</p>
                    <p className="text-xs text-slate-500">{row.customerEmail}</p>
                  </td>
                  <td className="p-3">
                    <span className={`inline-block rounded-full px-2 py-0.5 text-xs ${statusTone(row.status)}`}>
                      {formatTicketStatus(row.status)}
                    </span>
                  </td>
                  <td className="p-3">
                    <span className={`inline-block rounded-full px-2 py-0.5 text-xs ${priorityTone(row.priority)}`}>
                      {row.priority}
                    </span>
                  </td>
                  <td className="p-3 text-sm">{row.assignedAdminName || <span className="text-slate-400">Unassigned</span>}</td>
                  <td className="p-3 text-xs">
                    {row.slaBreached ? (
                      <span className="font-medium text-rose-700">Breached</span>
                    ) : row.slaDeadlineAt ? (
                      <span className="text-slate-600">{new Date(row.slaDeadlineAt).toLocaleString()}</span>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className="p-3 text-xs text-slate-500">
                    {row.createdAt ? new Date(row.createdAt).toLocaleString() : "—"}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
