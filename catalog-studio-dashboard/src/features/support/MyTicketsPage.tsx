import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { apiErrorMessage } from "../../api/client";
import { AdminTableBar, SortHeader } from "../admin/AdminTableBar";
import { supportApi } from "./api";
import {
  formatTicketStatus,
  priorityTone,
  statusTone,
  TICKET_STATUSES,
} from "./types";

export function MyTicketsPage() {
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState("updatedAt,desc");

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebounced(query.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  const list = useQuery({
    queryKey: ["support-tickets", debounced, status, page, sort],
    queryFn: () =>
      supportApi.listTickets({
        q: debounced || undefined,
        status: status || undefined,
        page,
        size: 20,
        sort,
      }),
  });

  const rows = list.data?.content || [];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">My support tickets</h1>
          <p className="mt-1 text-sm text-slate-500">Track open and past requests with Catalog Studio support.</p>
        </div>
        <Link
          to="/support"
          className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white"
        >
          New chat / ticket
        </Link>
      </div>

      <label className="inline-block text-sm">
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
          {TICKET_STATUSES.map((s) => (
            <option key={s} value={s}>
              {formatTicketStatus(s)}
            </option>
          ))}
        </select>
      </label>

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
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="p-3">
                <SortHeader label="Number" field="ticketNumber" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th className="p-3">
                <SortHeader label="Subject" field="subject" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th className="p-3">Status</th>
              <th className="p-3">Priority</th>
              <th className="p-3">
                <SortHeader label="Created" field="createdAt" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th className="p-3">
                <SortHeader label="Updated" field="updatedAt" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
            </tr>
          </thead>
          <tbody>
            {list.isLoading && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={6}>Loading tickets…</td>
              </tr>
            )}
            {list.isError && (
              <tr>
                <td className="p-6 text-red-600" colSpan={6}>
                  {apiErrorMessage(list.error, "Could not load tickets")}
                </td>
              </tr>
            )}
            {!list.isLoading && !list.isError && rows.length === 0 && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={6}>No tickets match this search.</td>
              </tr>
            )}
            {rows.map((row) => (
              <tr key={row.uuid} className="border-t hover:bg-slate-50">
                <td className="p-3">
                  <Link to={`/support/tickets/${row.uuid}`} className="font-medium text-teal-800 hover:underline">
                    {row.ticketNumber}
                  </Link>
                </td>
                <td className="p-3">{row.subject}</td>
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
                <td className="p-3 text-xs text-slate-500">
                  {row.createdAt ? new Date(row.createdAt).toLocaleString() : "—"}
                </td>
                <td className="p-3 text-xs text-slate-500">
                  {row.updatedAt ? new Date(row.updatedAt).toLocaleString() : "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
