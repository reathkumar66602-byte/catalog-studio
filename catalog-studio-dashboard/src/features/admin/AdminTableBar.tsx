import { ArrowDown, ArrowUp } from "lucide-react";

export function AdminTableBar({
  query,
  onQuery,
  page,
  totalPages,
  totalElements,
  onPage,
  noun,
}: {
  query: string;
  onQuery: (value: string) => void;
  page: number;
  totalPages: number;
  totalElements: number;
  onPage: (page: number) => void;
  noun: string;
}) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <input
        value={query}
        onChange={(event) => onQuery(event.target.value)}
        placeholder={`Search ${noun} by name, email, or phone`}
        className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm sm:max-w-sm"
      />
      <div className="flex items-center gap-3 text-sm text-slate-600">
        <span>
          {totalElements} {noun}
          {totalPages > 0 ? ` · page ${page + 1} of ${totalPages}` : ""}
        </span>
        <button
          type="button"
          disabled={page <= 0}
          onClick={() => onPage(Math.max(0, page - 1))}
          className="rounded-lg border px-3 py-1.5 disabled:opacity-50"
        >
          Previous
        </button>
        <button
          type="button"
          disabled={page + 1 >= totalPages}
          onClick={() => onPage(page + 1)}
          className="rounded-lg border px-3 py-1.5 disabled:opacity-50"
        >
          Next
        </button>
      </div>
    </div>
  );
}

export function SortHeader({
  label,
  field,
  sort,
  onSort,
}: {
  label: string;
  field: string;
  sort: string;
  onSort: (next: string) => void;
}) {
  const [currentField, direction] = sort.split(",");
  const active = currentField === field;
  const nextDir = active && direction === "asc" ? "desc" : "asc";
  return (
    <button
      type="button"
      onClick={() => onSort(`${field},${nextDir}`)}
      className="inline-flex items-center gap-1 font-medium text-slate-600 hover:text-slate-900"
    >
      {label}
      {active && (direction === "asc" ? <ArrowUp size={14} /> : <ArrowDown size={14} />)}
    </button>
  );
}

export function StatusBadge({ entitled, status }: { entitled: boolean; status?: string }) {
  return (
    <span className={entitled ? "font-medium text-emerald-700" : "font-medium text-amber-700"}>
      {status || (entitled ? "ACTIVE" : "EXPIRED")}
    </span>
  );
}
