import { useQuery } from "@tanstack/react-query";
import { PlayCircle } from "lucide-react";
import { api, apiErrorMessage } from "../../api/client";

type TipVideo = {
  id: string;
  title: string;
  publicUrl: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
};

function formatBytes(n: number) {
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

export function TipsPage() {
  const tips = useQuery({
    queryKey: ["ecommerce-tips"],
    queryFn: async () => (await api.get("/tips")).data.data as TipVideo[],
  });

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-4 md:p-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">E-commerce Tips</h1>
        <p className="mt-1 text-sm text-slate-500">
          Short seller tip videos for subscribed Catalog Studio users. Newest first.
        </p>
      </div>
      {tips.isLoading && <p className="text-sm text-slate-500">Loading tips…</p>}
      {tips.isError && <p className="text-sm text-red-600">{apiErrorMessage(tips.error)}</p>}
      {!tips.isLoading && (tips.data || []).length === 0 && (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-white px-6 py-12 text-center text-sm text-slate-500">
          No tip videos yet. Check back after your admin uploads one.
        </div>
      )}
      <div className="grid gap-5">
        {(tips.data || []).map((video) => (
          <article key={video.id} className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
            <div className="flex items-center gap-2 border-b border-slate-100 px-4 py-3">
              <PlayCircle className="h-5 w-5 text-teal-700" />
              <div className="min-w-0 flex-1">
                <h2 className="truncate font-semibold text-slate-900">{video.title}</h2>
                <p className="text-xs text-slate-500">
                  {formatBytes(video.sizeBytes)} · {new Date(video.createdAt).toLocaleString()}
                </p>
              </div>
            </div>
            <video
              className="aspect-video w-full bg-black"
              controls
              preload="metadata"
              src={video.publicUrl}
            >
              Your browser does not support tip videos.
            </video>
          </article>
        ))}
      </div>
    </div>
  );
}
