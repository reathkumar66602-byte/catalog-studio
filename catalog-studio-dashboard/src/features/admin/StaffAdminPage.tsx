import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, apiErrorMessage } from "../../api/client";
import { roleLabel } from "../../routes/roles";
import { AdminDialog } from "./AdminDialog";
import { AdminTableBar, SortHeader } from "./AdminTableBar";
import type { AdminUserRow, PageResult } from "./adminTypes";

export function StaffAdminPage() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<"ADMIN" | "USER">("ADMIN");
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState("createdAt,desc");
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState<{ row: AdminUserRow; action: "ACTIVATE" | "DEACTIVATE" } | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebounced(query.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    setPage(0);
  }, [tab]);

  const list = useQuery({
    queryKey: ["admin-staff", tab, debounced, page, sort],
    queryFn: async () =>
      (await api.get("/admin/workspace/staff", {
        params: { tab, q: debounced || undefined, page, size: 20, sort },
      })).data.data as PageResult<AdminUserRow>,
  });

  const changeRole = useMutation({
    mutationFn: (payload: { id: string; action: "ACTIVATE" | "DEACTIVATE" }) =>
      api.put(`/admin/workspace/staff/${payload.id}/role`, { action: payload.action }),
    onSuccess: async (_res, variables) => {
      setPending(null);
      setMessage(variables.action === "ACTIVATE" ? "This account is now an admin." : "This admin is now a normal user.");
      await qc.invalidateQueries({ queryKey: ["admin-staff"] });
    },
  });

  const rows = list.data?.content || [];

  function run(row: AdminUserRow, action: "ACTIVATE" | "DEACTIVATE") {
    setMessage("");
    setPending({ row, action });
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Admin</h1>
        <p className="mt-1 text-sm text-slate-500">
          Super admin only. On the Admin tab, Deactivate turns an admin into a normal user. On the User tab, Activate
          turns a seller into an admin. The super admin account cannot be changed here.
        </p>
      </div>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => setTab("ADMIN")}
          className={`rounded-xl px-4 py-2 text-sm font-medium ${
            tab === "ADMIN" ? "bg-teal-700 text-white" : "border border-slate-200 bg-white"
          }`}
        >
          Admins
        </button>
        <button
          type="button"
          onClick={() => setTab("USER")}
          className={`rounded-xl px-4 py-2 text-sm font-medium ${
            tab === "USER" ? "bg-teal-700 text-white" : "border border-slate-200 bg-white"
          }`}
        >
          Users
        </button>
      </div>
      <AdminTableBar
        query={query}
        onQuery={setQuery}
        page={page}
        totalPages={list.data?.totalPages || 0}
        totalElements={list.data?.totalElements || 0}
        onPage={setPage}
        noun={tab === "ADMIN" ? "admins" : "users"}
      />
      {message && <p className="text-sm text-teal-800">{message}</p>}
      {changeRole.isError && <p className="text-sm text-red-600">{apiErrorMessage(changeRole.error)}</p>}
      <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="p-3">
                <SortHeader label="Name" field="name" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th>
                <SortHeader label="Email" field="email" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th>
                <SortHeader label="Role" field="role" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th>
                <SortHeader label="Status" field="status" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th>
                <SortHeader label="Joined" field="createdAt" sort={sort} onSort={(next) => { setSort(next); setPage(0); }} />
              </th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {list.isLoading && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={6}>Loading...</td>
              </tr>
            )}
            {list.isError && (
              <tr>
                <td className="p-6 text-red-600" colSpan={6}>{apiErrorMessage(list.error, "Could not load accounts")}</td>
              </tr>
            )}
            {!list.isLoading && !list.isError && rows.length === 0 && (
              <tr>
                <td className="p-6 text-slate-500" colSpan={6}>No accounts on this tab.</td>
              </tr>
            )}
            {rows.map((row) => {
              const locked = row.role === "SUPERADMIN";
              return (
                <tr key={row.id} className="border-t">
                  <td className="p-3 font-medium">{row.name}</td>
                  <td>{row.email}</td>
                  <td>{row.displayRole || roleLabel(row.role)}</td>
                  <td>{row.status}</td>
                  <td>{row.createdAt ? new Date(row.createdAt).toLocaleDateString("en-IN") : "—"}</td>
                  <td className="p-3">
                    {locked ? (
                      <span className="text-xs text-slate-500">Protected</span>
                    ) : tab === "ADMIN" ? (
                      <button
                        type="button"
                        disabled={changeRole.isPending}
                        onClick={() => run(row, "DEACTIVATE")}
                        className="rounded-lg border border-rose-200 px-3 py-1.5 text-rose-700"
                      >
                        Deactivate
                      </button>
                    ) : (
                      <button
                        type="button"
                        disabled={changeRole.isPending}
                        onClick={() => run(row, "ACTIVATE")}
                        className="rounded-lg bg-teal-700 px-3 py-1.5 text-white"
                      >
                        Activate
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {pending && (
        <AdminDialog
          title={pending.action === "ACTIVATE" ? "Make this account an admin" : "Turn this admin into a user"}
          confirmLabel={pending.action === "ACTIVATE" ? "Activate as admin" : "Deactivate to user"}
          danger={pending.action === "DEACTIVATE"}
          pending={changeRole.isPending}
          onConfirm={() => changeRole.mutate({ id: pending.row.id, action: pending.action })}
          onClose={() => setPending(null)}
        >
          {pending.action === "ACTIVATE" ? (
            <p>
              {pending.row.email} will see Users, Access, Website settings, Trial and payment, and Email templates.
              Seller tools stay available. Super admin stays protected.
            </p>
          ) : (
            <p>
              {pending.row.email} will become a normal seller and lose staff menus. Listings and the current plan stay
              on the account.
            </p>
          )}
        </AdminDialog>
      )}
    </div>
  );
}
