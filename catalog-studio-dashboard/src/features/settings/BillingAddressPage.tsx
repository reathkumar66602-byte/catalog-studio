import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, apiErrorMessage } from "../../api/client";

type BillingAddress = {
  businessName?: string;
  gstNumber?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
  landmark?: string;
  googlePlaceId?: string;
  latitude?: number;
  longitude?: number;
};

type PlaceSuggestion = { placeId: string; description: string };
type PlaceDetails = {
  placeId: string;
  formattedAddress: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  latitude?: number;
  longitude?: number;
};

export function BillingAddressPage() {
  const qc = useQueryClient();
  const { data, isLoading, isError, error: loadError, refetch } = useQuery({
    queryKey: ["billing-address"],
    queryFn: async () => (await api.get("/me/billing-address")).data.data as BillingAddress,
  });
  const [form, setForm] = useState<BillingAddress>({ country: "India" });
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState<PlaceSuggestion[]>([]);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const debounce = useRef<number | undefined>(undefined);

  useEffect(() => {
    if (data) {
      setForm({ country: "India", ...data });
      setQuery(data.addressLine1 || "");
    }
  }, [data]);

  useEffect(() => {
    window.clearTimeout(debounce.current);
    if (query.trim().length < 3) {
      setSuggestions([]);
      return;
    }
    debounce.current = window.setTimeout(async () => {
      try {
        const { data: res } = await api.get("/places/autocomplete", { params: { query } });
        setSuggestions(res.data || []);
      } catch {
        setSuggestions([]);
      }
    }, 280);
    return () => window.clearTimeout(debounce.current);
  }, [query]);

  const composed = useMemo(
    () =>
      [form.addressLine1, form.addressLine2, form.landmark, form.city, form.state, form.postalCode, form.country]
        .filter(Boolean)
        .join(", "),
    [form],
  );

  function setField<K extends keyof BillingAddress>(key: K, value: BillingAddress[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function choosePlace(place: PlaceSuggestion) {
    setSuggestions([]);
    setQuery(place.description);
    try {
      const { data: res } = await api.get<{ data: PlaceDetails }>("/places/details", {
        params: { placeId: place.placeId },
      });
      const details = res.data;
      setForm((current) => ({
        ...current,
        addressLine1: details.addressLine1 || place.description,
        addressLine2: details.addressLine2 || current.addressLine2,
        city: details.city,
        state: details.state,
        postalCode: details.postalCode,
        country: details.country || "India",
        googlePlaceId: details.placeId,
        latitude: details.latitude,
        longitude: details.longitude,
      }));
    } catch (err) {
      setError(apiErrorMessage(err, "Could not fill that address"));
    }
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setMessage("");
    try {
      const { data: res } = await api.put("/me/billing-address", form);
      const saved = res.data as BillingAddress;
      setForm({ country: "India", ...saved });
      setQuery(saved.addressLine1 || query);
      await qc.invalidateQueries({ queryKey: ["billing-address"] });
      setMessage("Billing address saved");
    } catch (err) {
      setError(apiErrorMessage(err, "Could not save billing address"));
    } finally {
      setSaving(false);
    }
  }

  if (isLoading) {
    return <p className="text-slate-500">Loading billing address...</p>;
  }
  if (isError) {
    return (
      <div className="space-y-3">
        <p className="text-rose-700">{apiErrorMessage(loadError, "Could not load billing address")}</p>
        <button type="button" className="text-sm text-teal-700" onClick={() => refetch()}>
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Billing address</h1>
        <p className="mt-1 text-sm text-slate-500">
          Start typing your address. Google Places fills city, state, PIN, and country when a suggestion is selected.
        </p>
      </div>
      <form onSubmit={save} className="space-y-4 rounded-2xl border border-slate-200 bg-white p-6">
        {error && <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
        {message && <p className="rounded-xl bg-emerald-50 px-3 py-2 text-sm text-emerald-800">{message}</p>}
        <label className="relative block text-sm font-medium text-slate-700">
          Search address
          <input
            value={query}
            onChange={(event) => {
              setQuery(event.target.value);
              setField("addressLine1", event.target.value);
            }}
            placeholder="Start typing house no, street, area..."
            className="mt-1 w-full rounded-xl border px-3 py-2.5 outline-none focus:border-teal-600"
          />
          {suggestions.length > 0 && (
            <ul className="absolute z-20 mt-1 max-h-64 w-full overflow-auto rounded-xl border border-slate-200 bg-white shadow-lg">
              {suggestions.map((item) => (
                <li key={item.placeId}>
                  <button
                    type="button"
                    onClick={() => choosePlace(item)}
                    className="block w-full px-3 py-2 text-left text-sm hover:bg-slate-50"
                  >
                    {item.description}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </label>
        <input
          value={form.addressLine2 || ""}
          onChange={(event) => setField("addressLine2", event.target.value)}
          className="w-full rounded-xl border px-3 py-2.5"
          placeholder="Address line 2"
        />
        <input
          value={form.landmark || ""}
          onChange={(event) => setField("landmark", event.target.value)}
          className="w-full rounded-xl border px-3 py-2.5"
          placeholder="Landmark"
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <input
            value={form.city || ""}
            onChange={(event) => setField("city", event.target.value)}
            className="rounded-xl border px-3 py-2.5"
            placeholder="City"
          />
          <input
            value={form.state || ""}
            onChange={(event) => setField("state", event.target.value)}
            className="rounded-xl border px-3 py-2.5"
            placeholder="State"
          />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <input
            value={form.postalCode || ""}
            onChange={(event) => setField("postalCode", event.target.value)}
            className="rounded-xl border px-3 py-2.5"
            placeholder="PIN / postal code"
          />
          <input
            value={form.country || "India"}
            onChange={(event) => setField("country", event.target.value)}
            className="rounded-xl border px-3 py-2.5"
            placeholder="Country"
          />
        </div>
        <input
          value={form.gstNumber || ""}
          onChange={(event) => setField("gstNumber", event.target.value)}
          className="w-full rounded-xl border px-3 py-2.5"
          placeholder="GST number"
        />
        {composed && <p className="text-xs text-slate-500">{composed}</p>}
        <button disabled={saving} className="rounded-xl bg-teal-700 px-4 py-2 text-white disabled:opacity-60">
          {saving ? "Saving..." : "Save billing address"}
        </button>
      </form>
    </div>
  );
}
