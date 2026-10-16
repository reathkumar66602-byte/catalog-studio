import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import { isSuperAdmin } from "../../routes/roles";
import { useAuth } from "../../store/auth";
import { PlatformOmsPanel } from "./PlatformOmsPanel";

export function MarketplacesPage() {
  const { user } = useAuth();
  const superAdmin = isSuperAdmin(user?.role);
  const qc = useQueryClient();
  const { data } = useQuery({ queryKey: ["marketplaces"], queryFn: async () => (await api.get("/marketplaces")).data.data });
  const configure = useMutation({
    mutationFn: (name: string) => api.post(`/marketplaces/${name}/configure`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["marketplaces"] }),
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Marketplace connections</h1>
        <p className="text-slate-500">
          This Catalog Studio page manages extension autofill and (for Super admin) Base.com-style OMS sync. Marketplace
          API secrets stay in the server <code>.env</code> / <code>.env.gcp</code> property file — not in the browser.
        </p>
      </div>

      {superAdmin ? (
        <PlatformOmsPanel />
      ) : (
        <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          Platform OMS sync is Super admin only. Credentials are configured on the server property file. You can still
          configure the Chrome extension below.
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-3">
        {(data || []).map((item: { marketplace: string; status: string; features: string[] }) => (
          <article key={item.marketplace} className="rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-900">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold">{item.marketplace}</h3>
              <span className={`rounded-full px-2 py-1 text-xs ${item.status === "CONNECTED" ? "bg-emerald-100 text-emerald-800" : "bg-slate-100 text-slate-600"}`}>
                {item.status === "CONNECTED" ? "Connected" : "Not connected"}
              </span>
            </div>
            <ul className="mt-4 space-y-1 text-sm text-slate-600">
              {(item.features || []).map((f) => (
                <li key={f}>{f}</li>
              ))}
            </ul>
            <button onClick={() => configure.mutate(item.marketplace)} className="mt-5 w-full rounded-xl bg-teal-700 py-2 text-white">
              Configure extension
            </button>
          </article>
        ))}
      </div>
    </div>
  );
}
