import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent } from "react";
import { api, apiErrorMessage } from "../../api/client";
import type { AdminSiteBundle, SiteClient, SitePromo } from "../site/types";

export function SiteAdminPage() {
  const qc = useQueryClient();
  const { data, isLoading, error } = useQuery({
    queryKey: ["admin-site"],
    queryFn: async () => (await api.get("/admin/site")).data.data as AdminSiteBundle,
  });

  if (isLoading) return <p>Loading website settings...</p>;
  if (error || !data) return <p className="text-red-600">{apiErrorMessage(error, "Could not load website settings")}</p>;

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold">Website settings</h1>
        <p className="text-slate-500">
          Branding, {data.clients.find((c) => c.featured)?.storeName || "client"} details, promo codes, support email, and the
          enquiry form all come from the database.
        </p>
      </div>
      <SettingsForm settings={data.settings} onSaved={() => qc.invalidateQueries({ queryKey: ["admin-site"] })} />
      <ClientsPanel clients={data.clients} onSaved={() => qc.invalidateQueries({ queryKey: ["admin-site"] })} />
      <PromosPanel
        clients={data.clients}
        promos={data.promoCodes}
        onSaved={() => qc.invalidateQueries({ queryKey: ["admin-site"] })}
      />
      <EnquiriesPanel bundle={data} onSaved={() => qc.invalidateQueries({ queryKey: ["admin-site"] })} />
    </div>
  );
}

function SettingsForm({
  settings,
  onSaved,
}: {
  settings: AdminSiteBundle["settings"];
  onSaved: () => void;
}) {
  const save = useMutation({
    mutationFn: (payload: Record<string, unknown>) => api.put("/admin/site", payload),
    onSuccess: onSaved,
  });

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const f = new FormData(event.currentTarget);
    save.mutate({
      siteName: f.get("siteName"),
      tagline: f.get("tagline"),
      heroTitle: f.get("heroTitle"),
      heroSubtitle: f.get("heroSubtitle"),
      logoUrl: f.get("logoUrl"),
      primaryColor: f.get("primaryColor"),
      accentColor: f.get("accentColor"),
      heroBackground: f.get("heroBackground"),
      footerText: f.get("footerText"),
      supportEmail: f.get("supportEmail"),
      mailFromEmail: f.get("mailFromEmail"),
      mailFromName: f.get("mailFromName"),
      supportPhone: f.get("supportPhone"),
      enquiryEnabled: f.get("enquiryEnabled") === "on",
      enquiryIntro: f.get("enquiryIntro"),
      enquirySuccessMessage: f.get("enquirySuccessMessage"),
    });
  }

  return (
    <form onSubmit={onSubmit} className="space-y-3 rounded-2xl border border-slate-200 bg-white p-6">
      <h2 className="font-medium">Branding and support</h2>
      <Field name="siteName" label="Site name" defaultValue={settings.siteName} />
      <Field name="tagline" label="Tagline" defaultValue={settings.tagline} />
      <Field name="heroTitle" label="Hero title" defaultValue={settings.heroTitle} />
      <label className="block text-sm font-medium">
        Hero subtitle
        <textarea name="heroSubtitle" defaultValue={settings.heroSubtitle} rows={4} className="mt-1 w-full rounded-xl border px-3 py-2" />
      </label>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field name="logoUrl" label="Logo URL" defaultValue={settings.logoUrl} />
        <Field name="primaryColor" label="Primary color" defaultValue={settings.primaryColor} />
        <Field name="accentColor" label="Accent color" defaultValue={settings.accentColor} />
        <Field name="heroBackground" label="Hero background" defaultValue={settings.heroBackground} />
        <Field name="supportEmail" label="Support inbox (OTP replies and enquiries)" defaultValue={settings.supportEmail} required />
        <Field name="mailFromEmail" label="From address (must be verified in ZeptoMail)" defaultValue={settings.mailFromEmail || settings.supportEmail} />
        <Field name="mailFromName" label="From name" defaultValue={settings.mailFromName || "Catalog Studio"} />
        <Field name="supportPhone" label="Support phone" defaultValue={settings.supportPhone} />
      </div>
      <label className="block text-sm font-medium">
        Footer text
        <textarea name="footerText" defaultValue={settings.footerText} rows={3} className="mt-1 w-full rounded-xl border px-3 py-2" />
      </label>
      <label className="flex items-center gap-2 text-sm">
        <input type="checkbox" name="enquiryEnabled" defaultChecked={settings.enquiryEnabled} />
        Enquiry form enabled
      </label>
      <label className="block text-sm font-medium">
        Enquiry intro
        <textarea name="enquiryIntro" defaultValue={settings.enquiryIntro} rows={3} className="mt-1 w-full rounded-xl border px-3 py-2" />
      </label>
      <Field name="enquirySuccessMessage" label="Enquiry success message" defaultValue={settings.enquirySuccessMessage} />
      <button disabled={save.isPending} className="rounded-xl bg-teal-700 px-4 py-2 text-white">
        {save.isPending ? "Saving..." : "Save branding"}
      </button>
      {save.isError && <p className="text-sm text-red-600">{apiErrorMessage(save.error)}</p>}
    </form>
  );
}

function ClientsPanel({ clients, onSaved }: { clients: SiteClient[]; onSaved: () => void }) {
  const featured = clients.find((c) => c.featured) || clients[0];
  const save = useMutation({
    mutationFn: (payload: Record<string, unknown>) =>
      featured?.id
        ? api.put(`/admin/site/clients/${featured.id}`, payload)
        : api.post("/admin/site/clients", payload),
    onSuccess: onSaved,
  });

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const f = new FormData(event.currentTarget);
    save.mutate({
      storeName: f.get("storeName"),
      slug: f.get("slug"),
      ownerName: f.get("ownerName"),
      email: f.get("email"),
      phone: f.get("phone"),
      address: f.get("address"),
      websiteUrl: f.get("websiteUrl"),
      logoUrl: f.get("logoUrl"),
      tagline: f.get("tagline"),
      about: f.get("about"),
      branding: {
        primaryColor: f.get("brandPrimary"),
        accentColor: f.get("brandAccent"),
      },
      featured: true,
      status: "ACTIVE",
    });
  }

  return (
    <form onSubmit={onSubmit} className="space-y-3 rounded-2xl border border-slate-200 bg-white p-6">
      <h2 className="font-medium">Featured client</h2>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field name="storeName" label="Store name" defaultValue={featured?.storeName} required />
        <Field name="slug" label="Slug" defaultValue={featured?.slug} required />
        <Field name="ownerName" label="Owner" defaultValue={featured?.ownerName} />
        <Field name="email" label="Email" defaultValue={featured?.email} />
        <Field name="phone" label="Phone" defaultValue={featured?.phone} />
        <Field name="address" label="Address" defaultValue={featured?.address} />
        <Field name="websiteUrl" label="Website" defaultValue={featured?.websiteUrl} />
        <Field name="logoUrl" label="Logo URL" defaultValue={featured?.logoUrl} />
        <Field
          name="brandPrimary"
          label="Store primary color"
          defaultValue={String(featured?.branding?.primaryColor || "")}
        />
        <Field
          name="brandAccent"
          label="Store accent color"
          defaultValue={String(featured?.branding?.accentColor || "")}
        />
      </div>
      <Field name="tagline" label="Tagline" defaultValue={featured?.tagline} />
      <label className="block text-sm font-medium">
        About
        <textarea name="about" defaultValue={featured?.about} rows={4} className="mt-1 w-full rounded-xl border px-3 py-2" />
      </label>
      <button disabled={save.isPending} className="rounded-xl bg-teal-700 px-4 py-2 text-white">
        {save.isPending ? "Saving..." : "Save client"}
      </button>
      {save.isError && <p className="text-sm text-red-600">{apiErrorMessage(save.error)}</p>}
    </form>
  );
}

function PromosPanel({
  clients,
  promos,
  onSaved,
}: {
  clients: SiteClient[];
  promos: SitePromo[];
  onSaved: () => void;
}) {
  const clientId = clients.find((c) => c.featured)?.id || clients[0]?.id;
  const save = useMutation({
    mutationFn: (payload: Record<string, unknown>) => api.post("/admin/site/promo-codes", payload),
    onSuccess: onSaved,
  });
  const update = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Record<string, unknown> }) =>
      api.put(`/admin/site/promo-codes/${id}`, payload),
    onSuccess: onSaved,
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.delete(`/admin/site/promo-codes/${id}`),
    onSuccess: onSaved,
  });

  function onCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const f = new FormData(event.currentTarget);
    save.mutate({
      clientId,
      code: f.get("code"),
      headline: f.get("headline"),
      description: f.get("description"),
      discountType: f.get("discountType"),
      discountValue: Number(f.get("discountValue") || 0),
      trialDays: Number(f.get("trialDays") || 0),
      validUntil: f.get("validUntil") || null,
      status: "ACTIVE",
    });
    event.currentTarget.reset();
  }

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6">
      <h2 className="font-medium">Store promo codes</h2>
      <div className="space-y-2">
        {promos.map((promo) => (
          <div key={promo.id || promo.code} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border px-4 py-3">
            <div>
              <p className="font-mono font-semibold">{promo.code}</p>
              <p className="text-sm text-slate-500">
                {promo.headline} · {promo.discountType} {promo.discountValue}
              </p>
            </div>
            <div className="flex gap-2">
              <button
                className="text-sm text-slate-600"
                onClick={() =>
                  promo.id &&
                  update.mutate({
                    id: promo.id,
                    payload: { ...promo, status: promo.status === "ACTIVE" ? "DISABLED" : "ACTIVE" },
                  })
                }
              >
                {promo.status === "ACTIVE" ? "Disable" : "Enable"}
              </button>
              {promo.id && (
                <button className="text-sm text-red-600" onClick={() => remove.mutate(promo.id!)}>
                  Delete
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
      <form onSubmit={onCreate} className="grid gap-3 sm:grid-cols-2">
        <Field name="code" label="New code" required />
        <Field name="headline" label="Headline" />
        <Field name="description" label="Description" />
        <label className="text-sm font-medium">
          Type
          <select name="discountType" className="mt-1 w-full rounded-xl border px-3 py-2" defaultValue="PERCENT">
            <option value="PERCENT">Percent</option>
            <option value="FIXED">Fixed</option>
            <option value="TRIAL">Trial</option>
          </select>
        </label>
        <Field name="discountValue" label="Value" defaultValue="10" />
        <Field name="trialDays" label="Trial days" defaultValue="0" />
        <Field name="validUntil" label="Valid until" defaultValue="2027-12-31" />
        <div className="flex items-end">
          <button disabled={save.isPending || !clientId} className="rounded-xl bg-teal-700 px-4 py-2 text-white">
            Add promo
          </button>
        </div>
      </form>
      {save.isError && <p className="text-sm text-red-600">{apiErrorMessage(save.error)}</p>}
    </section>
  );
}

function EnquiriesPanel({ bundle, onSaved }: { bundle: AdminSiteBundle; onSaved: () => void }) {
  const update = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      api.post(`/admin/site/enquiries/${id}/status`, { status }),
    onSuccess: onSaved,
  });
  return (
    <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-6">
      <h2 className="font-medium">Enquiries</h2>
      {bundle.enquiries.length === 0 && <p className="text-sm text-slate-500">No enquiries yet.</p>}
      {bundle.enquiries.map((item) => (
        <div key={item.id} className="rounded-xl border px-4 py-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="font-medium">
              {item.name} · {item.email}
            </p>
            <select
              value={item.status}
              className="rounded-lg border px-2 py-1 text-sm"
              onChange={(e) => update.mutate({ id: item.id, status: e.target.value })}
            >
              <option>NEW</option>
              <option>READ</option>
              <option>REPLIED</option>
              <option>ARCHIVED</option>
            </select>
          </div>
          <p className="text-xs text-slate-500">
            {item.storeName || "No store"} · {item.subject || "No subject"} · {item.createdAt}
          </p>
          <p className="mt-2 text-sm text-slate-700">{item.message}</p>
        </div>
      ))}
    </section>
  );
}

function Field({
  name,
  label,
  defaultValue,
  required,
}: {
  name: string;
  label: string;
  defaultValue?: string;
  required?: boolean;
}) {
  return (
    <label className="block text-sm font-medium">
      {label}
      <input
        name={name}
        required={required}
        defaultValue={defaultValue}
        className="mt-1 w-full rounded-xl border px-3 py-2"
      />
    </label>
  );
}
