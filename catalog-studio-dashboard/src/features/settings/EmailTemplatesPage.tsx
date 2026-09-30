import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
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

type CampaignTemplate = {
  id: string;
  slug: string;
  name: string;
  channel: string;
  campaignType: string;
  subject?: string | null;
  bodyText: string;
  bodyHtml?: string | null;
  variables?: string[];
  enabled: boolean;
};

type Tab = "account" | "campaign";

const CAMPAIGN_TYPE_LABEL: Record<string, string> = {
  NO_PURCHASE: "No purchase yet",
  TRIAL_EXPIRED: "Trial expired",
  EXPIRING_SOON: "Expiring soon",
};

export function EmailTemplatesPage() {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>("account");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const emails = useQuery({
    queryKey: ["email-templates"],
    queryFn: async () => (await api.get("/admin/email-templates")).data.data as EmailTemplate[],
  });
  const campaigns = useQuery({
    queryKey: ["campaign-templates"],
    queryFn: async () => (await api.get("/admin/campaigns/templates")).data.data as CampaignTemplate[],
  });

  const [emailId, setEmailId] = useState<string | null>(null);
  const [campaignId, setCampaignId] = useState<string | null>(null);
  const [emailDraft, setEmailDraft] = useState<EmailTemplate | null>(null);
  const [campaignDraft, setCampaignDraft] = useState<CampaignTemplate | null>(null);

  const selectedEmail = useMemo(
    () => emails.data?.find((item) => item.id === emailId) ?? emails.data?.[0],
    [emails.data, emailId],
  );
  const selectedCampaign = useMemo(
    () => campaigns.data?.find((item) => item.id === campaignId) ?? campaigns.data?.[0],
    [campaigns.data, campaignId],
  );

  useEffect(() => {
    if (selectedEmail) {
      setEmailId(selectedEmail.id);
      setEmailDraft(selectedEmail);
    }
  }, [selectedEmail?.id, emails.data]);

  useEffect(() => {
    if (selectedCampaign) {
      setCampaignId(selectedCampaign.id);
      setCampaignDraft(selectedCampaign);
    }
  }, [selectedCampaign?.id, campaigns.data]);

  const saveEmail = useMutation({
    mutationFn: async () => {
      if (!emailDraft) return;
      await api.put(`/admin/email-templates/${emailDraft.id}`, {
        slug: emailDraft.slug,
        name: emailDraft.name,
        subject: emailDraft.subject,
        htmlBody: emailDraft.htmlBody,
        textBody: emailDraft.textBody,
        variables: emailDraft.variables,
        enabled: emailDraft.enabled,
      });
    },
    onSuccess: async () => {
      setMessage("Account email template saved. Outbound mail will use this content.");
      setError("");
      await queryClient.invalidateQueries({ queryKey: ["email-templates"] });
    },
    onError: (err) => setError(apiErrorMessage(err, "Could not save template")),
  });

  const saveCampaign = useMutation({
    mutationFn: async () => {
      if (!campaignDraft) return;
      await api.put(`/admin/campaigns/templates/${campaignDraft.id}`, {
        name: campaignDraft.name,
        subject: campaignDraft.subject || null,
        bodyText: campaignDraft.bodyText,
        bodyHtml: campaignDraft.bodyHtml || null,
        variables: campaignDraft.variables,
        enabled: campaignDraft.enabled,
      });
    },
    onSuccess: async () => {
      setMessage("Campaign template saved. Next campaign run will use this structure.");
      setError("");
      await queryClient.invalidateQueries({ queryKey: ["campaign-templates"] });
    },
    onError: (err) => setError(apiErrorMessage(err, "Could not save campaign template")),
  });

  const loading = emails.isLoading || campaigns.isLoading;
  if (loading) {
    return <p className="text-slate-500">Loading templates...</p>;
  }

  const isWhatsApp = campaignDraft?.channel === "WHATSAPP";

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Email & campaign templates</h1>
        <p className="mt-1 text-sm text-slate-500">
          Edit stored templates used for account mail and campaign email / WhatsApp. Placeholders:{" "}
          <code>{"{{name}}"}</code>, <code>{"{{email}}"}</code>, <code>{"{{loginLink}}"}</code>,{" "}
          <code>{"{{promoLine}}"}</code>, <code>{"{{promoCode}}"}</code>, <code>{"{{plan}}"}</code>,{" "}
          <code>{"{{endDate}}"}</code>, <code>{"{{daysLeft}}"}</code>, <code>{"{{otp}}"}</code>.
        </p>
      </div>

      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => {
            setTab("account");
            setMessage("");
            setError("");
          }}
          className={`rounded-xl px-4 py-2 text-sm ${
            tab === "account" ? "bg-teal-700 text-white" : "bg-slate-100 text-slate-700 hover:bg-slate-200"
          }`}
        >
          Account emails
        </button>
        <button
          type="button"
          onClick={() => {
            setTab("campaign");
            setMessage("");
            setError("");
          }}
          className={`rounded-xl px-4 py-2 text-sm ${
            tab === "campaign" ? "bg-teal-700 text-white" : "bg-slate-100 text-slate-700 hover:bg-slate-200"
          }`}
        >
          Campaigns (email & WhatsApp)
        </button>
      </div>

      {error && <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
      {message && <p className="rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{message}</p>}

      {tab === "account" && (
        <div className="grid gap-6 lg:grid-cols-[240px_minmax(0,1fr)]">
          <div className="space-y-2">
            {emails.data?.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setEmailId(item.id)}
                className={`block w-full rounded-xl px-3 py-2 text-left text-sm ${
                  item.id === emailId ? "bg-teal-50 font-medium text-teal-900" : "hover:bg-slate-100"
                }`}
              >
                {item.name}
                <span className="mt-0.5 block text-xs text-slate-500">{item.slug}</span>
              </button>
            ))}
          </div>
          {emailDraft && (
            <form
              className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5"
              onSubmit={(event) => {
                event.preventDefault();
                saveEmail.mutate();
              }}
            >
              <input
                value={emailDraft.name}
                onChange={(event) => setEmailDraft({ ...emailDraft, name: event.target.value })}
                className="w-full rounded-xl border px-3 py-2"
              />
              <input
                value={emailDraft.subject}
                onChange={(event) => setEmailDraft({ ...emailDraft, subject: event.target.value })}
                className="w-full rounded-xl border px-3 py-2"
                placeholder="Subject"
              />
              <textarea
                value={emailDraft.htmlBody}
                onChange={(event) => setEmailDraft({ ...emailDraft, htmlBody: event.target.value })}
                className="min-h-64 w-full rounded-xl border px-3 py-2 font-mono text-sm"
              />
              <textarea
                value={emailDraft.textBody || ""}
                onChange={(event) => setEmailDraft({ ...emailDraft, textBody: event.target.value })}
                className="min-h-24 w-full rounded-xl border px-3 py-2 font-mono text-sm"
                placeholder="Plain text body"
              />
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={emailDraft.enabled}
                  onChange={(event) => setEmailDraft({ ...emailDraft, enabled: event.target.checked })}
                />
                Enabled
              </label>
              <button disabled={saveEmail.isPending} className="rounded-xl bg-teal-700 px-4 py-2 text-white">
                {saveEmail.isPending ? "Saving..." : "Save template"}
              </button>
            </form>
          )}
        </div>
      )}

      {tab === "campaign" && (
        <div className="grid gap-6 lg:grid-cols-[260px_minmax(0,1fr)]">
          <div className="space-y-2">
            {campaigns.data?.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setCampaignId(item.id)}
                className={`block w-full rounded-xl px-3 py-2 text-left text-sm ${
                  item.id === campaignId ? "bg-teal-50 font-medium text-teal-900" : "hover:bg-slate-100"
                }`}
              >
                {item.name}
                <span className="mt-0.5 block text-xs text-slate-500">
                  {item.channel} · {CAMPAIGN_TYPE_LABEL[item.campaignType] || item.campaignType}
                </span>
              </button>
            ))}
          </div>
          {campaignDraft && (
            <form
              className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5"
              onSubmit={(event) => {
                event.preventDefault();
                saveCampaign.mutate();
              }}
            >
              <p className="text-xs text-slate-500">
                {campaignDraft.channel} · {CAMPAIGN_TYPE_LABEL[campaignDraft.campaignType] || campaignDraft.campaignType}{" "}
                · <code>{campaignDraft.slug}</code>
              </p>
              <input
                value={campaignDraft.name}
                onChange={(event) => setCampaignDraft({ ...campaignDraft, name: event.target.value })}
                className="w-full rounded-xl border px-3 py-2"
              />
              {!isWhatsApp && (
                <input
                  value={campaignDraft.subject || ""}
                  onChange={(event) => setCampaignDraft({ ...campaignDraft, subject: event.target.value })}
                  className="w-full rounded-xl border px-3 py-2"
                  placeholder="Email subject"
                />
              )}
              {!isWhatsApp && (
                <textarea
                  value={campaignDraft.bodyHtml || ""}
                  onChange={(event) => setCampaignDraft({ ...campaignDraft, bodyHtml: event.target.value })}
                  className="min-h-56 w-full rounded-xl border px-3 py-2 font-mono text-sm"
                  placeholder="HTML body"
                />
              )}
              <textarea
                value={campaignDraft.bodyText || ""}
                onChange={(event) => setCampaignDraft({ ...campaignDraft, bodyText: event.target.value })}
                className={`w-full rounded-xl border px-3 py-2 font-mono text-sm ${isWhatsApp ? "min-h-40" : "min-h-24"}`}
                placeholder={isWhatsApp ? "WhatsApp message text" : "Plain text body"}
              />
              {campaignDraft.variables && campaignDraft.variables.length > 0 && (
                <p className="text-xs text-slate-500">
                  Variables: {campaignDraft.variables.map((v) => `{{${v}}}`).join(", ")}
                </p>
              )}
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={campaignDraft.enabled}
                  onChange={(event) => setCampaignDraft({ ...campaignDraft, enabled: event.target.checked })}
                />
                Enabled
              </label>
              <button disabled={saveCampaign.isPending} className="rounded-xl bg-teal-700 px-4 py-2 text-white">
                {saveCampaign.isPending ? "Saving..." : "Save template"}
              </button>
            </form>
          )}
        </div>
      )}
    </div>
  );
}
