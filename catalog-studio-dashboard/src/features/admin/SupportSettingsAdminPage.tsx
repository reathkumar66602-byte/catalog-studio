import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiErrorMessage } from "../../api/client";
import { supportApi } from "../support/api";

type Tab = "knowledge" | "sla" | "categories";

type KnowledgeDraft = {
  uuid?: string;
  slug: string;
  title: string;
  summary: string;
  body: string;
  locale: string;
  category: string;
  tags: string;
  active: boolean;
  verified: boolean;
  sortOrder: number;
};

type CategoryDraft = {
  uuid?: string;
  code: string;
  name: string;
  description: string;
  active: boolean;
  sortOrder: number;
};

type HolidayRow = { holidayDate: string; name: string };

const emptyArticle = (): KnowledgeDraft => ({
  slug: "",
  title: "",
  summary: "",
  body: "",
  locale: "en",
  category: "GENERAL",
  tags: "",
  active: true,
  verified: true,
  sortOrder: 100,
});

const emptyCategory = (): CategoryDraft => ({
  code: "",
  name: "",
  description: "",
  active: true,
  sortOrder: 100,
});

export function SupportSettingsAdminPage() {
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>("knowledge");
  const [draft, setDraft] = useState<KnowledgeDraft>(emptyArticle());
  const [categoryDraft, setCategoryDraft] = useState<CategoryDraft>(emptyCategory());
  const [holidays, setHolidays] = useState<HolidayRow[]>([]);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const knowledge = useQuery({
    queryKey: ["support-knowledge"],
    queryFn: () => supportApi.listKnowledge(),
    enabled: tab === "knowledge",
  });

  const sla = useQuery({
    queryKey: ["support-sla"],
    queryFn: () => supportApi.getSla(),
    enabled: tab === "sla",
  });

  const categories = useQuery({
    queryKey: ["support-admin-categories"],
    queryFn: () => supportApi.adminCategories(),
    enabled: tab === "categories",
  });

  const [slaForm, setSlaForm] = useState({
    name: "Default",
    timezone: "Asia/Kolkata",
    workingDays: "MON,TUE,WED,THU,FRI",
    workStart: "09:00:00",
    workEnd: "18:00:00",
    initialResponseBusinessDays: 1,
    warnHoursBefore: 4,
    active: true,
  });

  useEffect(() => {
    if (!sla.data) return;
    setSlaForm({
      name: sla.data.name,
      timezone: sla.data.timezone,
      workingDays: sla.data.workingDays,
      workStart: String(sla.data.workStart).slice(0, 8),
      workEnd: String(sla.data.workEnd).slice(0, 8),
      initialResponseBusinessDays: sla.data.initialResponseBusinessDays,
      warnHoursBefore: sla.data.warnHoursBefore,
      active: sla.data.active,
    });
    setHolidays(
      (sla.data.holidays || []).map((h) => ({
        holidayDate: h.holidayDate,
        name: h.name || "",
      })),
    );
  }, [sla.data]);

  const saveArticle = useMutation({
    mutationFn: () =>
      supportApi.saveKnowledge(
        {
          slug: draft.slug.trim(),
          title: draft.title.trim(),
          summary: draft.summary.trim() || undefined,
          body: draft.body.trim(),
          locale: draft.locale,
          category: draft.category || undefined,
          tags: draft.tags || undefined,
          active: draft.active,
          verified: draft.verified,
          sortOrder: draft.sortOrder,
        },
        draft.uuid,
      ),
    onSuccess: async () => {
      setMessage(draft.uuid ? "Article updated." : "Article created.");
      setError("");
      setDraft(emptyArticle());
      await qc.invalidateQueries({ queryKey: ["support-knowledge"] });
    },
    onError: (err) => setError(apiErrorMessage(err, "Could not save article")),
  });

  const removeArticle = useMutation({
    mutationFn: (uuid: string) => supportApi.deleteKnowledge(uuid),
    onSuccess: async () => {
      setMessage("Article deactivated.");
      await qc.invalidateQueries({ queryKey: ["support-knowledge"] });
    },
    onError: (err) => setError(apiErrorMessage(err, "Could not deactivate article")),
  });

  const saveSla = useMutation({
    mutationFn: () =>
      supportApi.saveSla({
        ...slaForm,
        holidays: holidays
          .filter((h) => h.holidayDate.trim())
          .map((h) => ({
            holidayDate: h.holidayDate.trim(),
            name: h.name.trim() || "Holiday",
          })),
      }),
    onSuccess: async () => {
      setMessage("SLA configuration saved.");
      setError("");
      await qc.invalidateQueries({ queryKey: ["support-sla"] });
    },
    onError: (err) => setError(apiErrorMessage(err, "Could not save SLA")),
  });

  const saveCategory = useMutation({
    mutationFn: () => {
      const payload = {
        code: categoryDraft.code.trim(),
        name: categoryDraft.name.trim(),
        description: categoryDraft.description.trim() || undefined,
        active: categoryDraft.active,
        sortOrder: categoryDraft.sortOrder,
      };
      return categoryDraft.uuid
        ? supportApi.updateCategory(categoryDraft.uuid, payload)
        : supportApi.createCategory(payload);
    },
    onSuccess: async () => {
      setMessage(categoryDraft.uuid ? "Category updated." : "Category created.");
      setError("");
      setCategoryDraft(emptyCategory());
      await qc.invalidateQueries({ queryKey: ["support-admin-categories"] });
    },
    onError: (err) => setError(apiErrorMessage(err, "Could not save category")),
  });

  const removeCategory = useMutation({
    mutationFn: (uuid: string) => supportApi.deleteCategory(uuid),
    onSuccess: async () => {
      setMessage("Category deactivated.");
      await qc.invalidateQueries({ queryKey: ["support-admin-categories"] });
    },
    onError: (err) => setError(apiErrorMessage(err, "Could not deactivate category")),
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Support settings</h1>
          <p className="mt-1 text-sm text-slate-500">
            Manage knowledge base articles, ticket categories, and the one-business-day response SLA.
          </p>
        </div>
        <Link to="/admin/support-tickets" className="text-sm font-medium text-teal-700 hover:underline">
          ← Support tickets
        </Link>
      </div>

      <div className="flex flex-wrap gap-2">
        {(["knowledge", "categories", "sla"] as Tab[]).map((item) => (
          <button
            key={item}
            type="button"
            onClick={() => {
              setTab(item);
              setMessage("");
              setError("");
            }}
            className={`rounded-xl px-4 py-2 text-sm font-medium ${
              tab === item ? "bg-teal-700 text-white" : "border border-slate-200 bg-white text-slate-700"
            }`}
          >
            {item === "knowledge" ? "Knowledge base" : item === "categories" ? "Categories" : "SLA"}
          </button>
        ))}
      </div>

      {message && <p className="rounded-xl border border-teal-200 bg-teal-50 px-3 py-2 text-sm text-teal-800">{message}</p>}
      {error && <p className="rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</p>}

      {tab === "knowledge" && (
        <div className="grid gap-6 lg:grid-cols-2">
          <form
            className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5"
            onSubmit={(event) => {
              event.preventDefault();
              saveArticle.mutate();
            }}
          >
            <h2 className="font-semibold text-slate-900">{draft.uuid ? "Edit article" : "New article"}</h2>
            <input
              required
              value={draft.slug}
              onChange={(e) => setDraft({ ...draft, slug: e.target.value })}
              placeholder="slug"
              className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
            />
            <input
              required
              value={draft.title}
              onChange={(e) => setDraft({ ...draft, title: e.target.value })}
              placeholder="Title"
              className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
            />
            <textarea
              value={draft.summary}
              onChange={(e) => setDraft({ ...draft, summary: e.target.value })}
              placeholder="Summary"
              className="min-h-[70px] w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
            />
            <textarea
              required
              value={draft.body}
              onChange={(e) => setDraft({ ...draft, body: e.target.value })}
              placeholder="Article body"
              className="min-h-[160px] w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
            />
            <div className="grid grid-cols-2 gap-3">
              <input
                value={draft.locale}
                onChange={(e) => setDraft({ ...draft, locale: e.target.value })}
                placeholder="Locale"
                className="rounded-xl border border-slate-200 px-3 py-2 text-sm"
              />
              <input
                value={draft.category}
                onChange={(e) => setDraft({ ...draft, category: e.target.value })}
                placeholder="Category"
                className="rounded-xl border border-slate-200 px-3 py-2 text-sm"
              />
            </div>
            <input
              value={draft.tags}
              onChange={(e) => setDraft({ ...draft, tags: e.target.value })}
              placeholder="Tags (comma-separated)"
              className="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm"
            />
            <div className="flex flex-wrap gap-4 text-sm text-slate-700">
              <label className="inline-flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={draft.active}
                  onChange={(e) => setDraft({ ...draft, active: e.target.checked })}
                />
                Active
              </label>
              <label className="inline-flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={draft.verified}
                  onChange={(e) => setDraft({ ...draft, verified: e.target.checked })}
                />
                Verified
              </label>
            </div>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={saveArticle.isPending}
                className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
              >
                {saveArticle.isPending ? "Saving…" : "Save article"}
              </button>
              {draft.uuid && (
                <button
                  type="button"
                  onClick={() => setDraft(emptyArticle())}
                  className="rounded-xl border border-slate-200 px-4 py-2 text-sm"
                >
                  Cancel edit
                </button>
              )}
            </div>
          </form>

          <div className="rounded-2xl border border-slate-200 bg-white">
            <div className="border-b border-slate-100 px-4 py-3 text-sm font-medium text-slate-700">
              Articles {knowledge.data ? `(${knowledge.data.length})` : ""}
            </div>
            <ul className="divide-y divide-slate-100">
              {knowledge.isLoading && <li className="p-4 text-sm text-slate-500">Loading…</li>}
              {(knowledge.data || []).map((article) => (
                <li key={article.uuid} className="flex items-start justify-between gap-3 p-4">
                  <div>
                    <p className="font-medium text-slate-900">{article.title}</p>
                    <p className="text-xs text-slate-500">
                      {article.slug} · {article.locale} · {article.active ? "active" : "inactive"}
                      {article.verified ? " · verified" : ""}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      className="text-sm text-teal-700 hover:underline"
                      onClick={() =>
                        setDraft({
                          uuid: article.uuid,
                          slug: article.slug,
                          title: article.title,
                          summary: article.summary || "",
                          body: article.body,
                          locale: article.locale,
                          category: article.category || "",
                          tags: article.tags || "",
                          active: article.active,
                          verified: article.verified,
                          sortOrder: article.sortOrder,
                        })
                      }
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      className="text-sm text-rose-700 hover:underline"
                      onClick={() => removeArticle.mutate(article.uuid)}
                    >
                      Deactivate
                    </button>
                  </div>
                </li>
              ))}
              {!knowledge.isLoading && (knowledge.data || []).length === 0 && (
                <li className="p-4 text-sm text-slate-500">No articles yet.</li>
              )}
            </ul>
          </div>
        </div>
      )}

      {tab === "categories" && (
        <div className="grid gap-6 lg:grid-cols-2">
          <form
            className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5"
            onSubmit={(event) => {
              event.preventDefault();
              saveCategory.mutate();
            }}
          >
            <h2 className="font-semibold text-slate-900">
              {categoryDraft.uuid ? "Edit category" : "New category"}
            </h2>
            <label className="block text-sm">
              <span className="mb-1 block text-slate-500">Code</span>
              <input
                required
                value={categoryDraft.code}
                onChange={(e) => setCategoryDraft({ ...categoryDraft, code: e.target.value })}
                placeholder="BILLING"
                className="w-full rounded-xl border border-slate-200 px-3 py-2"
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block text-slate-500">Name</span>
              <input
                required
                value={categoryDraft.name}
                onChange={(e) => setCategoryDraft({ ...categoryDraft, name: e.target.value })}
                placeholder="Billing"
                className="w-full rounded-xl border border-slate-200 px-3 py-2"
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block text-slate-500">Description</span>
              <textarea
                value={categoryDraft.description}
                onChange={(e) => setCategoryDraft({ ...categoryDraft, description: e.target.value })}
                className="min-h-[80px] w-full rounded-xl border border-slate-200 px-3 py-2"
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block text-slate-500">Sort order</span>
              <input
                type="number"
                value={categoryDraft.sortOrder}
                onChange={(e) =>
                  setCategoryDraft({ ...categoryDraft, sortOrder: Number(e.target.value) || 0 })
                }
                className="w-full rounded-xl border border-slate-200 px-3 py-2"
              />
            </label>
            <label className="inline-flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={categoryDraft.active}
                onChange={(e) => setCategoryDraft({ ...categoryDraft, active: e.target.checked })}
              />
              Active
            </label>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={saveCategory.isPending}
                className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
              >
                {saveCategory.isPending ? "Saving…" : "Save category"}
              </button>
              {categoryDraft.uuid && (
                <button
                  type="button"
                  onClick={() => setCategoryDraft(emptyCategory())}
                  className="rounded-xl border border-slate-200 px-4 py-2 text-sm"
                >
                  Cancel edit
                </button>
              )}
            </div>
          </form>

          <div className="rounded-2xl border border-slate-200 bg-white">
            <div className="border-b border-slate-100 px-4 py-3 text-sm font-medium text-slate-700">
              Categories {categories.data ? `(${categories.data.length})` : ""}
            </div>
            <ul className="divide-y divide-slate-100">
              {categories.isLoading && <li className="p-4 text-sm text-slate-500">Loading…</li>}
              {(categories.data || []).map((cat) => (
                <li key={cat.uuid} className="flex items-start justify-between gap-3 p-4">
                  <div>
                    <p className="font-medium text-slate-900">{cat.name}</p>
                    <p className="text-xs text-slate-500">
                      {cat.code} · sort {cat.sortOrder} · {cat.active ? "active" : "inactive"}
                    </p>
                    {cat.description && <p className="mt-1 text-sm text-slate-600">{cat.description}</p>}
                  </div>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      className="text-sm text-teal-700 hover:underline"
                      onClick={() =>
                        setCategoryDraft({
                          uuid: cat.uuid,
                          code: cat.code,
                          name: cat.name,
                          description: cat.description || "",
                          active: cat.active,
                          sortOrder: cat.sortOrder,
                        })
                      }
                    >
                      Edit
                    </button>
                    {cat.active && (
                      <button
                        type="button"
                        className="text-sm text-rose-700 hover:underline"
                        onClick={() => removeCategory.mutate(cat.uuid)}
                      >
                        Deactivate
                      </button>
                    )}
                  </div>
                </li>
              ))}
              {!categories.isLoading && (categories.data || []).length === 0 && (
                <li className="p-4 text-sm text-slate-500">No categories yet.</li>
              )}
            </ul>
          </div>
        </div>
      )}

      {tab === "sla" && (
        <form
          className="max-w-xl space-y-3 rounded-2xl border border-slate-200 bg-white p-5"
          onSubmit={(event) => {
            event.preventDefault();
            saveSla.mutate();
          }}
        >
          <h2 className="font-semibold text-slate-900">Initial response SLA</h2>
          <p className="text-sm text-slate-500">
            Default: one business day in Asia/Kolkata, Monday–Friday 09:00–18:00. Weekends and holidays are skipped.
          </p>
          {(
            [
              ["name", "Name"],
              ["timezone", "Timezone"],
              ["workingDays", "Working days"],
              ["workStart", "Work start (HH:mm:ss)"],
              ["workEnd", "Work end (HH:mm:ss)"],
            ] as const
          ).map(([key, label]) => (
            <label key={key} className="block text-sm">
              <span className="mb-1 block text-slate-500">{label}</span>
              <input
                required
                value={slaForm[key]}
                onChange={(e) => setSlaForm({ ...slaForm, [key]: e.target.value })}
                className="w-full rounded-xl border border-slate-200 px-3 py-2"
              />
            </label>
          ))}
          <label className="block text-sm">
            <span className="mb-1 block text-slate-500">Initial response business days</span>
            <input
              type="number"
              min={1}
              required
              value={slaForm.initialResponseBusinessDays}
              onChange={(e) =>
                setSlaForm({ ...slaForm, initialResponseBusinessDays: Number(e.target.value) || 1 })
              }
              className="w-full rounded-xl border border-slate-200 px-3 py-2"
            />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block text-slate-500">Warn hours before breach</span>
            <input
              type="number"
              min={0}
              required
              value={slaForm.warnHoursBefore}
              onChange={(e) => setSlaForm({ ...slaForm, warnHoursBefore: Number(e.target.value) || 0 })}
              className="w-full rounded-xl border border-slate-200 px-3 py-2"
            />
          </label>
          <label className="inline-flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={slaForm.active}
              onChange={(e) => setSlaForm({ ...slaForm, active: e.target.checked })}
            />
            Active
          </label>

          <div className="space-y-2 rounded-xl border border-slate-100 bg-slate-50 p-3">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-sm font-medium text-slate-800">Holidays</h3>
              <button
                type="button"
                className="text-sm font-medium text-teal-700 hover:underline"
                onClick={() => setHolidays([...holidays, { holidayDate: "", name: "" }])}
              >
                Add holiday
              </button>
            </div>
            {holidays.length === 0 && (
              <p className="text-xs text-slate-500">No holidays configured.</p>
            )}
            {holidays.map((row, index) => (
              <div key={index} className="flex flex-wrap items-end gap-2">
                <label className="text-sm">
                  <span className="mb-1 block text-slate-500">Date</span>
                  <input
                    type="date"
                    required
                    value={row.holidayDate}
                    onChange={(e) => {
                      const next = [...holidays];
                      next[index] = { ...next[index], holidayDate: e.target.value };
                      setHolidays(next);
                    }}
                    className="rounded-xl border border-slate-200 bg-white px-3 py-2"
                  />
                </label>
                <label className="min-w-[10rem] flex-1 text-sm">
                  <span className="mb-1 block text-slate-500">Name</span>
                  <input
                    value={row.name}
                    onChange={(e) => {
                      const next = [...holidays];
                      next[index] = { ...next[index], name: e.target.value };
                      setHolidays(next);
                    }}
                    placeholder="Holiday name"
                    className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2"
                  />
                </label>
                <button
                  type="button"
                  className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-rose-700"
                  onClick={() => setHolidays(holidays.filter((_, i) => i !== index))}
                >
                  Remove
                </button>
              </div>
            ))}
          </div>

          <button
            type="submit"
            disabled={saveSla.isPending}
            className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
          >
            {saveSla.isPending ? "Saving…" : "Save SLA"}
          </button>
        </form>
      )}
    </div>
  );
}
