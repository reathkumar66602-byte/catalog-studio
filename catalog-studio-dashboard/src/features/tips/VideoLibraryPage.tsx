import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Download, MessageCircle, PlayCircle, Send } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { api, apiErrorMessage } from "../../api/client";
import { useI18n } from "../../i18n/LanguageProvider";
import type { LocaleCode } from "../../i18n/catalog";

type VideoLang = "EN" | "HI" | "BN";

type TipVideo = {
  id: string;
  title: string;
  kind?: string;
  language?: string;
  seriesKey?: string;
  publicUrl: string;
  downloadUrl?: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
};

type TipComment = {
  id: string;
  tipVideoId: string;
  parentId?: string | null;
  body: string;
  userName: string;
  staffReply?: boolean;
  createdAt: string;
};

type LessonGroup = {
  seriesKey: string;
  title: string;
  variants: Partial<Record<VideoLang, TipVideo>>;
};

const LANG_ORDER: VideoLang[] = ["EN", "HI", "BN"];

function formatBytes(n: number) {
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

function normalizeLang(raw?: string): VideoLang {
  const value = (raw || "EN").toUpperCase();
  if (value === "HI" || value === "BN") return value;
  return "EN";
}

function preferredLang(locale: LocaleCode): VideoLang {
  if (locale === "hi") return "HI";
  if (locale === "bn") return "BN";
  return "EN";
}

function downloadHref(video: TipVideo) {
  if (video.downloadUrl) return video.downloadUrl;
  return video.publicUrl.includes("?") ? `${video.publicUrl}&download=1` : `${video.publicUrl}?download=1`;
}

function groupLessons(videos: TipVideo[]): LessonGroup[] {
  const map = new Map<string, LessonGroup>();
  for (const video of videos) {
    const seriesKey = video.seriesKey || video.id;
    const language = normalizeLang(video.language);
    const existing = map.get(seriesKey);
    if (existing) {
      existing.variants[language] = video;
      if (!existing.title) existing.title = video.title;
      continue;
    }
    map.set(seriesKey, {
      seriesKey,
      title: video.title,
      variants: { [language]: video },
    });
  }
  return [...map.values()];
}

function pickInitialLang(group: LessonGroup, locale: LocaleCode): VideoLang {
  const preferred = preferredLang(locale);
  if (group.variants[preferred]) return preferred;
  return LANG_ORDER.find((lang) => group.variants[lang]) || "EN";
}

export function VideoLibraryPage({
  apiPath,
  titleKey,
  subtitleKey,
  queryKey,
}: {
  apiPath: "/app-guide" | "/tutorials";
  titleKey: string;
  subtitleKey: string;
  queryKey: string;
}) {
  const { t } = useI18n();
  const videos = useQuery({
    queryKey: [queryKey],
    queryFn: async () => (await api.get(apiPath)).data.data as TipVideo[],
  });
  const lessons = useMemo(() => groupLessons(videos.data || []), [videos.data]);

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-4 md:p-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{t(titleKey)}</h1>
        <p className="mt-1 text-sm text-slate-500">{t(subtitleKey)}</p>
        <p className="mt-2 text-xs text-slate-500">{t("tips.langHint")}</p>
      </div>
      {videos.isLoading && <p className="text-sm text-slate-500">{t("tips.loading")}</p>}
      {videos.isError && <p className="text-sm text-red-600">{apiErrorMessage(videos.error)}</p>}
      {!videos.isLoading && lessons.length === 0 && (
        <div className="rounded-2xl border border-dashed border-slate-200 bg-white px-6 py-12 text-center text-sm text-slate-500">
          {t("tips.empty")}
        </div>
      )}
      <div className="grid gap-5">
        {lessons.map((lesson) => (
          <LessonCard key={lesson.seriesKey} lesson={lesson} apiPath={apiPath} queryKey={queryKey} />
        ))}
      </div>
    </div>
  );
}

function LessonCard({
  lesson,
  apiPath,
  queryKey,
}: {
  lesson: LessonGroup;
  apiPath: "/app-guide" | "/tutorials";
  queryKey: string;
}) {
  const { t, locale } = useI18n();
  const qc = useQueryClient();
  const [lang, setLang] = useState<VideoLang>(() => pickInitialLang(lesson, locale));
  const [draft, setDraft] = useState("");
  const [open, setOpen] = useState(false);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const wakeLockRef = useRef<WakeLockSentinel | null>(null);

  const available = LANG_ORDER.filter((code) => lesson.variants[code]);
  const active = lesson.variants[lang] || lesson.variants[available[0]];

  useEffect(() => {
    if (!lesson.variants[lang] && available[0]) {
      setLang(available[0]);
    }
    // Only re-sync when the active language disappears from the series.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lesson.seriesKey, lang, lesson.variants.EN?.id, lesson.variants.HI?.id, lesson.variants.BN?.id]);

  async function requestWakeLock() {
    try {
      if (!("wakeLock" in navigator)) return;
      wakeLockRef.current = await navigator.wakeLock.request("screen");
      wakeLockRef.current.addEventListener("release", () => {
        wakeLockRef.current = null;
      });
    } catch {
      // Browser may deny wake lock (battery, permissions).
    }
  }

  async function releaseWakeLock() {
    try {
      await wakeLockRef.current?.release();
    } catch {
      // ignore
    } finally {
      wakeLockRef.current = null;
    }
  }

  useEffect(() => {
    const onVisibility = () => {
      const el = videoRef.current;
      if (document.visibilityState === "visible" && el && !el.paused && !el.ended) {
        void requestWakeLock();
      }
    };
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      document.removeEventListener("visibilitychange", onVisibility);
      void releaseWakeLock();
    };
  }, []);

  const comments = useQuery({
    queryKey: [queryKey, "comments", active?.id],
    queryFn: async () => (await api.get(`${apiPath}/${active!.id}/comments`)).data.data as TipComment[],
    enabled: open && Boolean(active?.id),
  });

  const post = useMutation({
    mutationFn: async () => api.post(`${apiPath}/${active!.id}/comments`, { body: draft.trim() }),
    onSuccess: async () => {
      setDraft("");
      await qc.invalidateQueries({ queryKey: [queryKey, "comments", active?.id] });
    },
  });

  if (!active) return null;

  function langLabel(code: VideoLang) {
    if (code === "HI") return t("tips.langHi");
    if (code === "BN") return t("tips.langBn");
    return t("tips.langEn");
  }

  return (
    <article className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
      <div className="flex flex-wrap items-center gap-2 border-b border-slate-100 px-4 py-3">
        <PlayCircle className="h-5 w-5 shrink-0 text-teal-700" />
        <div className="min-w-0 flex-1">
          <h2 className="truncate font-semibold text-slate-900">{lesson.title}</h2>
          <p className="text-xs text-slate-500">
            {formatBytes(active.sizeBytes)} · {new Date(active.createdAt).toLocaleString()}
          </p>
        </div>
        <a
          href={downloadHref(active)}
          download
          className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-50"
        >
          <Download className="h-3.5 w-3.5" />
          {t("tips.download")}
        </a>
      </div>

      {available.length > 1 && (
        <div className="flex flex-wrap gap-2 border-b border-slate-100 px-4 py-2.5">
          <span className="self-center text-xs font-medium text-slate-500">{t("tips.playIn")}</span>
          {available.map((code) => (
            <button
              key={code}
              type="button"
              onClick={() => {
                setLang(code);
                void releaseWakeLock();
              }}
              className={`rounded-full px-3 py-1 text-xs font-semibold ${
                lang === code
                  ? "bg-teal-700 text-white"
                  : "bg-slate-100 text-slate-700 hover:bg-slate-200"
              }`}
            >
              {langLabel(code)}
            </button>
          ))}
        </div>
      )}

      <video
        key={active.id}
        ref={videoRef}
        className="aspect-video w-full bg-black"
        controls
        preload="metadata"
        playsInline
        src={active.publicUrl}
        onPlay={() => void requestWakeLock()}
        onPause={() => void releaseWakeLock()}
        onEnded={() => void releaseWakeLock()}
      >
        {t("tips.unsupported")}
      </video>

      <div className="space-y-3 border-t border-slate-100 px-4 py-3">
        <button
          type="button"
          onClick={() => setOpen((value) => !value)}
          className="inline-flex items-center gap-1.5 text-sm font-medium text-teal-700 hover:underline"
        >
          <MessageCircle className="h-4 w-4" />
          {open ? t("tips.hideComments") : t("tips.showComments")}
        </button>
        {open && (
          <>
            {comments.isLoading && <p className="text-xs text-slate-500">{t("tips.loadingComments")}</p>}
            {comments.isError && (
              <p className="text-xs text-red-600">{apiErrorMessage(comments.error)}</p>
            )}
            <ul className="space-y-2">
              {(comments.data || []).map((comment) => (
                <li
                  key={comment.id}
                  className={`rounded-xl px-3 py-2 text-sm ${
                    comment.staffReply ? "bg-teal-50 text-teal-950" : "bg-slate-50 text-slate-700"
                  } ${comment.parentId ? "ml-4" : ""}`}
                >
                  <p className="text-xs font-semibold">
                    {comment.userName}
                    {comment.staffReply ? ` · ${t("tips.staffReply")}` : ""}
                  </p>
                  <p className="mt-0.5 whitespace-pre-wrap">{comment.body}</p>
                  <p className="mt-1 text-[11px] text-slate-400">
                    {new Date(comment.createdAt).toLocaleString()}
                  </p>
                </li>
              ))}
              {!comments.isLoading && (comments.data || []).length === 0 && (
                <li className="text-xs text-slate-500">{t("tips.noComments")}</li>
              )}
            </ul>
            <div className="flex gap-2">
              <input
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                maxLength={4000}
                placeholder={t("tips.commentPlaceholder")}
                className="min-w-0 flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm"
              />
              <button
                type="button"
                disabled={post.isPending || !draft.trim()}
                onClick={() => post.mutate()}
                className="inline-flex items-center gap-1 rounded-xl bg-teal-700 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
              >
                <Send className="h-4 w-4" />
                {t("tips.postComment")}
              </button>
            </div>
            {post.isError && <p className="text-xs text-red-600">{apiErrorMessage(post.error)}</p>}
          </>
        )}
      </div>
    </article>
  );
}
