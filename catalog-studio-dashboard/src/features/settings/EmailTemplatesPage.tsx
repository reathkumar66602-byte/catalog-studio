import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { api, apiErrorMessage } from "../../api/client";

type EmailTemplate = {
  id: string;
  slug: string;
  name: string;
  subject: string;
  htmlBody: string;
  textBody?: string;
  variables?: string[];
  enabled: boolean;
};

export function EmailTemplatesPage() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ["email-templates"],
    queryFn: async () => (await api.get("/admin/email-templates")).data.data as EmailTemplate[],
  });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = data?.find((item) => item.id === selectedId) ?? data?.[0];
  const [draft, setDraft] = useState<EmailTemplate | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (selected) {
      setSelectedId(selected.id);
      setDraft(selected);
    }
  }, [selected?.id, data]);

  const save = useMutation({
    mutationFn: async () => {
      if (!draft) return;
      await api.put(`/admin/email-templates/${draft.id}`, {
        slug: draft.slug,
        name: draft.name,
        subject: draft.subject,
        htmlBody: draft.htmlBody,
        textBody: draft.textBody,
        variables: draft.variables,
        enabled: draft.enabled,
      });
    },
    onSuccess: async () => {
      setMessage("Template saved. OTP emails will use this content.");
      setError("");
      await queryClient.invalidateQueries({ queryKey: ["email-templates"] });
    },
    onError: (err) => {
      setError(apiErrorMessage(err, "Could not save template"));
    },
  });

  if (isLoading) {
    return <p className="text-slate-500">Loading email templates...</p>;
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Email templates</h1>
        <p className="mt-1 text-sm text-slate-500">
          Stored in the database. Use placeholders like <code>{"{{otp}}"}</code>, <code>{"{{name}}"}</code>,{" "}
          <code>{"{{email}}"}</code>, <code>{"{{expiresMinutes}}"}</code>.
        </p>
      </div>
      <div className="grid gap-6 lg:grid-cols-[240px_minmax(0,1fr)]">
        <div className="space-y-2">
          {data?.map((item) => (
            <button
              key={item.id}
              onClick={() => setSelectedId(item.id)}
              className={`block w-full rounded-xl px-3 py-2 text-left text-sm ${
                item.id === selectedId ? "bg-teal-50 font-medium text-teal-900" : "hover:bg-slate-100"
              }`}
            >
              {item.name}
              <span className="mt-0.5 block text-xs text-slate-500">{item.slug}</span>
            </button>
          ))}
        </div>
        {draft && (
          <form
            className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5"
            onSubmit={(event) => {
              event.preventDefault();
              save.mutate();
            }}
          >
            {error && <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
            {message && <p className="rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{message}</p>}
            <input
              value={draft.name}
              onChange={(event) => setDraft({ ...draft, name: event.target.value })}
              className="w-full rounded-xl border px-3 py-2"
            />
            <input
              value={draft.subject}
              onChange={(event) => setDraft({ ...draft, subject: event.target.value })}
              className="w-full rounded-xl border px-3 py-2"
              placeholder="Subject"
            />
            <textarea
              value={draft.htmlBody}
              onChange={(event) => setDraft({ ...draft, htmlBody: event.target.value })}
              className="min-h-64 w-full rounded-xl border px-3 py-2 font-mono text-sm"
            />
            <textarea
              value={draft.textBody || ""}
              onChange={(event) => setDraft({ ...draft, textBody: event.target.value })}
              className="min-h-24 w-full rounded-xl border px-3 py-2 font-mono text-sm"
              placeholder="Plain text body"
            />
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={draft.enabled}
                onChange={(event) => setDraft({ ...draft, enabled: event.target.checked })}
              />
              Enabled
            </label>
            <button disabled={save.isPending} className="rounded-xl bg-teal-700 px-4 py-2 text-white">
              {save.isPending ? "Saving..." : "Save template"}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
