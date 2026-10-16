import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, apiErrorMessage } from "../../api/client";
import { isSuperAdmin } from "../../routes/roles";
import { useAuth } from "../../store/auth";

type TipVideo = {
  id: string;
  title: string;
  publicUrl: string;
  contentType: string;
  sizeBytes: number;
  uploadedByEmail?: string | null;
  createdAt: string;
};

type TipSettings = {
  maxCount: number;
  maxTotalBytes: number;
  usedBytes: number;
  videoCount: number;
};

function formatBytes(n: number) {
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / (1024 * 1024)).toFixed(1)} MB`;
  return `${(n / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

export function TipsAdminPage() {
  const { user } = useAuth();
  const qc = useQueryClient();
  const superAdmin = isSuperAdmin(user?.role);
  const [title, setTitle] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [maxCount, setMaxCount] = useState("5");
  const [maxTotalMb, setMaxTotalMb] = useState("500");
  const [message, setMessage] = useState("");

  const settings = useQuery({
    queryKey: ["admin-tip-settings"],
    queryFn: async () => (await api.get("/admin/tips/settings")).data.data as TipSettings,
  });
  const videos = useQuery({
    queryKey: ["admin-tip-videos"],
    queryFn: async () => (await api.get("/admin/tips")).data.data as TipVideo[],
  });

  const upload = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error("Choose a video file");
      const body = new FormData();
      body.append("title", title.trim());
      body.append("file", file);
      return api.post("/admin/tips", body);
    },
    onSuccess: async () => {
      setMessage("Tip video uploaded. Older videos are removed automatically when caps are exceeded.");
      setTitle("");
      setFile(null);
      await qc.invalidateQueries({ queryKey: ["admin-tip-videos"] });
      await qc.invalidateQueries({ queryKey: ["admin-tip-settings"] });
      await qc.invalidateQueries({ queryKey: ["ecommerce-tips"] });
    },
  });

  const remove = useMutation({
    mutationFn: (id: string) => api.delete(`/admin/tips/${id}`),
    onSuccess: async () => {
      setMessage("Tip video deleted.");
      await qc.invalidateQueries({ queryKey: ["admin-tip-videos"] });
      await qc.invalidateQueries({ queryKey: ["admin-tip-settings"] });
      await qc.invalidateQueries({ queryKey: ["ecommerce-tips"] });
    },
  });

  const saveSettings = useMutation({
    mutationFn: () =>
      api.put("/admin/tips/settings", {
        maxCount: Number(maxCount),
        maxTotalBytes: Math.round(Number(maxTotalMb) * 1024 * 1024),
      }),
    onSuccess: async () => {
      setMessage("Tip video caps updated.");
      await qc.invalidateQueries({ queryKey: ["admin-tip-settings"] });
      await qc.invalidateQueries({ queryKey: ["admin-tip-videos"] });
    },
  });

  const s = settings.data;

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-4 md:p-6">
      <div>
        <h1 className="text-2xl font-semibold">E-commerce Tips admin</h1>
        <p className="mt-1 text-sm text-slate-500">
          Upload tip videos for subscribed sellers. When count or total size is exceeded, the oldest video is removed
          (FIFO).
        </p>
      </div>

      {s && (
        <div className="rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-600">
          Library: {s.videoCount} / {s.maxCount} videos · {formatBytes(s.usedBytes)} / {formatBytes(s.maxTotalBytes)}
        </div>
      )}

      {superAdmin && (
        <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
          <h2 className="font-semibold">Super admin caps</h2>
          <div className="grid gap-3 md:grid-cols-3">
            <label className="text-sm">
              <span className="mb-1 block text-slate-500">Max videos</span>
              <input
                type="number"
                min={1}
                max={50}
                value={maxCount}
                onChange={(e) => setMaxCount(e.target.value)}
                className="w-full rounded-lg border px-3 py-2"
              />
            </label>
            <label className="text-sm">
              <span className="mb-1 block text-slate-500">Max total size (MB)</span>
              <input
                type="number"
                min={1}
                value={maxTotalMb}
                onChange={(e) => setMaxTotalMb(e.target.value)}
                className="w-full rounded-lg border px-3 py-2"
              />
            </label>
            <div className="flex items-end">
              <button
                type="button"
                disabled={saveSettings.isPending}
                onClick={() => {
                  setMessage("");
                  saveSettings.mutate();
                }}
                className="w-full rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
              >
                {saveSettings.isPending ? "Saving…" : "Save caps"}
              </button>
            </div>
          </div>
          {s && (
            <button
              type="button"
              className="text-xs text-teal-700 underline"
              onClick={() => {
                setMaxCount(String(s.maxCount));
                setMaxTotalMb(String(Math.round(s.maxTotalBytes / (1024 * 1024))));
              }}
            >
              Load current caps into form
            </button>
          )}
        </section>
      )}

      <section className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="font-semibold">Upload tip video</h2>
        <div className="grid gap-3 md:grid-cols-2">
          <label className="text-sm md:col-span-2">
            <span className="mb-1 block text-slate-500">Title</span>
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full rounded-lg border px-3 py-2"
              placeholder="e.g. Meesho photo tips"
              maxLength={200}
            />
          </label>
          <label className="text-sm md:col-span-2">
            <span className="mb-1 block text-slate-500">Video (MP4 / WebM / MOV, max 200MB each)</span>
            <input
              type="file"
              accept="video/mp4,video/webm,video/quicktime,.mp4,.webm,.mov"
              onChange={(e) => setFile(e.target.files?.[0] || null)}
              className="w-full rounded-lg border px-3 py-2"
            />
          </label>
        </div>
        <button
          type="button"
          disabled={upload.isPending || !title.trim() || !file}
          onClick={() => {
            setMessage("");
            upload.mutate();
          }}
          className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-60"
        >
          {upload.isPending ? "Uploading…" : "Upload tip video"}
        </button>
      </section>

      {message && <p className="text-sm text-teal-800">{message}</p>}
      {(upload.isError || remove.isError || saveSettings.isError) && (
        <p className="text-sm text-red-600">
          {apiErrorMessage(upload.error || remove.error || saveSettings.error)}
        </p>
      )}

      <section className="overflow-x-auto rounded-2xl border border-slate-200 bg-white p-5">
        <h2 className="mb-3 font-semibold">Library</h2>
        <table className="w-full min-w-[720px] text-left text-sm">
          <thead className="text-slate-500">
            <tr>
              <th className="py-2">Title</th>
              <th>Size</th>
              <th>Uploaded</th>
              <th>By</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {(videos.data || []).map((v) => (
              <tr key={v.id} className="border-t">
                <td className="py-2 font-medium">
                  <a href={v.publicUrl} target="_blank" rel="noreferrer" className="text-teal-700 underline">
                    {v.title}
                  </a>
                </td>
                <td>{formatBytes(v.sizeBytes)}</td>
                <td>{new Date(v.createdAt).toLocaleString()}</td>
                <td>{v.uploadedByEmail || "—"}</td>
                <td className="text-right">
                  <button
                    type="button"
                    className="text-red-600 underline disabled:opacity-50"
                    disabled={remove.isPending}
                    onClick={() => {
                      if (window.confirm(`Delete “${v.title}”?`)) remove.mutate(v.id);
                    }}
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {(videos.data || []).length === 0 && <p className="text-sm text-slate-500">No tip videos uploaded yet.</p>}
      </section>
    </div>
  );
}
