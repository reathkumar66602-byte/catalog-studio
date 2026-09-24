import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, apiErrorMessage } from "../../api/client";
import { AdminTableBar } from "./AdminTableBar";
import type { AdminUserRow, FeatureDefinition, PageResult } from "./adminTypes";

export function AccessAdminPage() {
  const qc = useQueryClient();
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState("name,asc");
  const [drafts, setDrafts] = useState<Record<string, Record<string, boolean>>>({});
  const [message, setMessage] = useState("");

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebounced(query.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  const catalog = useQuery({
    queryKey: ["admin-features"],
    queryFn: async () => (await api.get("/admin/workspace/features")).data.data as FeatureDefinition[],
  });
  const list = useQuery({
    queryKey: ["admin-access", debounced, page, sort],
    queryFn: async () =>
      (await api.get("/admin/workspace/access", { params: { q: debounced || undefined, page, size: 20, sort } }))
        .data.data as PageResult<AdminUserRow>,
  });

  const save = useMutation({
    mutationFn: (payload: { id: string; features: Record<string, boolean> }) =>
      api.put(`/admin/workspace/access/${payload.id}`, { features: payload.features }),
    onSuccess: async () => {
      setMessage("Menu access saved. The seller sees the change on the next refresh.");
      await qc.invalidateQueries({ queryKey: ["admin-access"] });
    },
  });

  const features = catalog.data || [];
  const rows = list.data?.content || [];

  function flagsFor(row: AdminUserRow) {
    return drafts[row.id] || row.features || {};
  }

  function toggle(row: AdminUserRow, key: string) {
    const current = flagsFor(row);
    setDrafts((all) => ({ ...all, [row.id]: { ...current, [key]: !current[key] } }));
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Access</h1>
        <p className="mt-1 text-sm text-slate-500">
          Hide or show workspace menus per seller. Example: turn on Meesho calculator only for the accounts that should
          see it on the dashboard. Dashboard, Subscription, and Settings stay on so a seller can always recharge and
          manage the account. Staff accounts always keep full access.
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
      {save.isError && <p className="text-sm text-red-600">{apiErrorMessage(save.error)}</p>}
      <div className="space-y-4">
        {list.isLoading && <p className="text-sm text-slate-500">Loading access...</p>}
        {list.isError && <p className="text-sm text-red-600">{apiErrorMessage(list.error, "Could not load access")}</p>}
        {!list.isLoading && rows.length === 0 && <p className="text-sm text-slate-500">No sellers match this search.</p>}
        {rows.map((row) => {
          const flags = flagsFor(row);
          return (
            <section key={row.id} className="rounded-2xl border border-slate-200 bg-white p-5">
              <div className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="font-medium">{row.name}</p>
                  <p className="text-sm text-slate-500">{row.email}</p>
                </div>
                <button
                  type="button"
                  disabled={save.isPending}
                  onClick={() => save.mutate({ id: row.id, features: flags })}
                  className="rounded-xl bg-teal-700 px-4 py-2 text-sm text-white"
                >
                  Save access
                </button>
              </div>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {features.map((feature) => (
                  <label key={feature.key} className="flex items-start gap-2 rounded-xl border border-slate-100 px-3 py-2 text-sm">
                    <input
                      type="checkbox"
                      className="mt-1"
                      checked={feature.required || flags[feature.key] !== false}
                      disabled={feature.required}
                      onChange={() => toggle(row, feature.key)}
                    />
                    <span>
                      <span className="font-medium">{feature.label}</span>
                      <span className="mt-0.5 block text-xs text-slate-500">{feature.description}</span>
                    </span>
                  </label>
                ))}
              </div>
            </section>
          );
        })}
      </div>
      <div className="flex flex-wrap gap-2 text-sm">
        <span className="text-slate-500">Sort</span>
        <button type="button" className="underline" onClick={() => { setSort("name,asc"); setPage(0); }}>Name</button>
        <button type="button" className="underline" onClick={() => { setSort("email,asc"); setPage(0); }}>Email</button>
        <button type="button" className="underline" onClick={() => { setSort("createdAt,desc"); setPage(0); }}>Newest</button>
      </div>
    </div>
  );
}
