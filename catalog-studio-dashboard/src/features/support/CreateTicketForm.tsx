import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { apiErrorMessage } from "../../api/client";
import { supportApi } from "./api";
import {
  FALLBACK_CATEGORY_CODES,
  TICKET_PRIORITIES,
  type CreateTicketPayload,
  type TicketResponse,
} from "./types";

type Draft = {
  subject: string;
  description: string;
  categoryCode: string;
  priority: string;
  files: File[];
};

const emptyDraft = (): Draft => ({
  subject: "",
  description: "",
  categoryCode: "OTHER",
  priority: "MEDIUM",
  files: [],
});

export function CreateTicketForm({
  sessionUuid,
  locale,
  initialSubject,
  initialDescription,
  onCreated,
  onCancel,
}: {
  sessionUuid?: string;
  locale?: string;
  initialSubject?: string;
  initialDescription?: string;
  onCreated: (ticket: TicketResponse) => void;
  onCancel?: () => void;
}) {
  const [step, setStep] = useState<"edit" | "confirm">("edit");
  const [draft, setDraft] = useState<Draft>(() => ({
    ...emptyDraft(),
    subject: initialSubject || "",
    description: initialDescription || "",
  }));
  const [error, setError] = useState("");

  const categories = useQuery({
    queryKey: ["support-categories"],
    queryFn: () => supportApi.listCategories(),
    staleTime: 60_000,
  });

  const categoryOptions = useMemo(() => {
    if (categories.data?.length) {
      return categories.data.map((c) => ({ code: c.code, name: c.name }));
    }
    return FALLBACK_CATEGORY_CODES.map((code) => ({ code, name: code.replaceAll("_", " ") }));
  }, [categories.data]);

  const submit = useMutation({
    mutationFn: async () => {
      const attachmentIds: string[] = [];
      for (const file of draft.files) {
        const uploaded = await supportApi.uploadPendingAttachment(file);
        attachmentIds.push(uploaded.uuid);
      }
      const payload: CreateTicketPayload = {
        sessionUuid,
        subject: draft.subject.trim(),
        description: draft.description.trim(),
        categoryCode: draft.categoryCode || undefined,
        priority: draft.priority || undefined,
        locale,
        attachmentIds: attachmentIds.length ? attachmentIds : undefined,
      };
      return supportApi.createTicket(payload);
    },
    onSuccess: (ticket) => onCreated(ticket),
    onError: (err) => setError(apiErrorMessage(err, "Could not create ticket")),
  });

  function validate() {
    if (!draft.subject.trim()) {
      setError("Subject is required");
      return false;
    }
    if (!draft.description.trim()) {
      setError("Description is required");
      return false;
    }
    setError("");
    return true;
  }

  if (step === "confirm") {
    return (
      <div className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5">
        <div>
          <h3 className="text-lg font-semibold">Confirm support ticket</h3>
          <p className="mt-1 text-sm text-slate-500">Review the details before submitting.</p>
        </div>
        <dl className="grid gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-slate-500">Subject</dt>
            <dd className="font-medium">{draft.subject}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Category</dt>
            <dd className="font-medium">
              {categoryOptions.find((c) => c.code === draft.categoryCode)?.name || draft.categoryCode}
            </dd>
          </div>
          <div>
            <dt className="text-slate-500">Priority</dt>
            <dd className="font-medium">{draft.priority}</dd>
          </div>
          <div>
            <dt className="text-slate-500">Attachments</dt>
            <dd className="font-medium">
              {draft.files.length === 0
                ? "None"
                : draft.files.map((f) => f.name).join(", ")}
            </dd>
          </div>
          <div className="sm:col-span-2">
            <dt className="text-slate-500">Description</dt>
            <dd className="mt-1 whitespace-pre-wrap rounded-xl bg-slate-50 p-3">{draft.description}</dd>
          </div>
        </dl>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            disabled={submit.isPending}
            onClick={() => submit.mutate()}
            className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
          >
            {submit.isPending ? "Submitting…" : "Submit ticket"}
          </button>
          <button
            type="button"
            disabled={submit.isPending}
            onClick={() => setStep("edit")}
            className="rounded-xl border border-slate-200 px-4 py-2 text-sm"
          >
            Back
          </button>
          {onCancel && (
            <button type="button" onClick={onCancel} className="rounded-xl px-4 py-2 text-sm text-slate-600">
              Cancel
            </button>
          )}
        </div>
      </div>
    );
  }

  return (
    <form
      className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5"
      onSubmit={(event) => {
        event.preventDefault();
        if (validate()) setStep("confirm");
      }}
    >
      <div>
        <h3 className="text-lg font-semibold">Create support ticket</h3>
        <p className="mt-1 text-sm text-slate-500">Tell us what you need help with.</p>
      </div>
      <label className="block text-sm">
        <span className="mb-1 block text-slate-500">Subject</span>
        <input
          value={draft.subject}
          onChange={(e) => setDraft((d) => ({ ...d, subject: e.target.value }))}
          className="w-full rounded-xl border border-slate-200 px-3 py-2"
          maxLength={255}
          required
        />
      </label>
      <label className="block text-sm">
        <span className="mb-1 block text-slate-500">Description</span>
        <textarea
          value={draft.description}
          onChange={(e) => setDraft((d) => ({ ...d, description: e.target.value }))}
          className="min-h-[120px] w-full rounded-xl border border-slate-200 px-3 py-2"
          maxLength={10000}
          required
        />
      </label>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="block text-sm">
          <span className="mb-1 block text-slate-500">Category</span>
          <select
            value={draft.categoryCode}
            onChange={(e) => setDraft((d) => ({ ...d, categoryCode: e.target.value }))}
            className="w-full rounded-xl border border-slate-200 px-3 py-2"
          >
            {categoryOptions.map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label className="block text-sm">
          <span className="mb-1 block text-slate-500">Priority</span>
          <select
            value={draft.priority}
            onChange={(e) => setDraft((d) => ({ ...d, priority: e.target.value }))}
            className="w-full rounded-xl border border-slate-200 px-3 py-2"
          >
            {TICKET_PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label className="block text-sm">
        <span className="mb-1 block text-slate-500">Attachments (images / PDF)</span>
        <input
          type="file"
          multiple
          accept="image/*,application/pdf"
          onChange={(e) =>
            setDraft((d) => ({ ...d, files: Array.from(e.target.files || []) }))
          }
          className="block w-full text-sm"
        />
        {draft.files.length > 0 && (
          <ul className="mt-2 space-y-1 text-xs text-slate-500">
            {draft.files.map((f) => (
              <li key={f.name + f.size}>{f.name}</li>
            ))}
          </ul>
        )}
      </label>
      {error && <p className="text-sm text-red-600">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white">
          Review & continue
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel} className="rounded-xl border border-slate-200 px-4 py-2 text-sm">
            Cancel
          </button>
        )}
      </div>
    </form>
  );
}
