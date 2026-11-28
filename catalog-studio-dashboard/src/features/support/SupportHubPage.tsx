import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { LifeBuoy, Send } from "lucide-react";
import { apiErrorMessage } from "../../api/client";
import { LOCALES, type LocaleCode, normalizeLocale } from "../../i18n/catalog";
import { useI18n } from "../../i18n/LanguageProvider";
import { supportApi } from "./api";
import { CreateTicketForm } from "./CreateTicketForm";
import { formatTicketStatus, priorityTone, statusTone, type ChatMessageResponse } from "./types";

type Tab = "chat" | "tickets";

const SESSION_KEY = "cs_support_chat_session";

export function SupportHubPage() {
  const { locale: uiLocale } = useI18n();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>("chat");
  const [chatLocale, setChatLocale] = useState<LocaleCode>(normalizeLocale(uiLocale));
  const [sessionUuid, setSessionUuid] = useState<string | null>(() => localStorage.getItem(SESSION_KEY));
  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [draft, setDraft] = useState("");
  const [showTicketForm, setShowTicketForm] = useState(false);
  const [error, setError] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);

  const tickets = useQuery({
    queryKey: ["support-tickets", "hub"],
    queryFn: () => supportApi.listTickets({ page: 0, size: 5, sort: "updatedAt,desc" }),
    enabled: tab === "tickets",
  });

  const loadMessages = useMutation({
    mutationFn: (uuid: string) => supportApi.listChatMessages(uuid),
    onSuccess: (list) => setMessages(list),
    onError: (err) => {
      localStorage.removeItem(SESSION_KEY);
      setSessionUuid(null);
      setMessages([]);
      setError(apiErrorMessage(err, "Could not load chat"));
    },
  });

  useEffect(() => {
    if (sessionUuid) loadMessages.mutate(sessionUuid);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- load once per session id
  }, [sessionUuid]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages.length]);

  const startSession = useMutation({
    mutationFn: () => supportApi.createChatSession(chatLocale),
    onSuccess: (session) => {
      localStorage.setItem(SESSION_KEY, session.uuid);
      setSessionUuid(session.uuid);
      setMessages([]);
      setError("");
    },
    onError: (err) => setError(apiErrorMessage(err, "Could not start chat")),
  });

  const sendMessage = useMutation({
    mutationFn: async (content: string) => {
      if (!sessionUuid) throw new Error("No session");
      const reply = await supportApi.postChatMessage(sessionUuid, content);
      const list = await supportApi.listChatMessages(sessionUuid);
      return { reply, list };
    },
    onSuccess: ({ reply, list }) => {
      setMessages(list);
      if (reply.suggestTicket) setShowTicketForm(true);
      setDraft("");
      setError("");
    },
    onError: (err) => setError(apiErrorMessage(err, "Could not send message")),
  });

  const changeLocale = useMutation({
    mutationFn: (next: LocaleCode) => {
      if (!sessionUuid) return Promise.resolve(null);
      return supportApi.updateChatLocale(sessionUuid, next);
    },
    onSuccess: (_res, next) => {
      setChatLocale(next);
      setError("");
    },
    onError: (err) => setError(apiErrorMessage(err, "Could not update language")),
  });

  const suggestTicket = messages.some((m) => m.suggestTicket);

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-semibold">
            <LifeBuoy className="text-teal-700" size={28} />
            Support
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Chat with Catalog Studio help, or open a ticket for the support team.
          </p>
        </div>
        <Link
          to="/support/tickets"
          className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm hover:bg-slate-50"
        >
          All my tickets
        </Link>
      </div>

      <div className="flex gap-2 border-b border-slate-200">
        {(["chat", "tickets"] as Tab[]).map((id) => (
          <button
            key={id}
            type="button"
            onClick={() => setTab(id)}
            className={`border-b-2 px-4 py-2 text-sm font-medium ${
              tab === id
                ? "border-teal-700 text-teal-800"
                : "border-transparent text-slate-500 hover:text-slate-800"
            }`}
          >
            {id === "chat" ? "AI chat" : "My tickets"}
          </button>
        ))}
      </div>

      {tab === "tickets" && (
        <div className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5">
          {tickets.isLoading && <p className="text-sm text-slate-500">Loading tickets…</p>}
          {tickets.isError && (
            <p className="text-sm text-red-600">{apiErrorMessage(tickets.error, "Could not load tickets")}</p>
          )}
          {!tickets.isLoading && !tickets.isError && (tickets.data?.content.length || 0) === 0 && (
            <p className="text-sm text-slate-500">No tickets yet. Start a chat or create one from the chat tab.</p>
          )}
          <ul className="divide-y divide-slate-100">
            {(tickets.data?.content || []).map((row) => (
              <li key={row.uuid}>
                <Link
                  to={`/support/tickets/${row.uuid}`}
                  className="flex flex-wrap items-center justify-between gap-2 py-3 hover:bg-slate-50"
                >
                  <div>
                    <p className="font-medium">{row.ticketNumber}</p>
                    <p className="text-sm text-slate-600">{row.subject}</p>
                  </div>
                  <div className="flex items-center gap-2 text-xs">
                    <span className={`rounded-full px-2 py-0.5 ${statusTone(row.status)}`}>
                      {formatTicketStatus(row.status)}
                    </span>
                    <span className={`rounded-full px-2 py-0.5 ${priorityTone(row.priority)}`}>
                      {row.priority}
                    </span>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <button
            type="button"
            onClick={() => {
              setTab("chat");
              setShowTicketForm(true);
            }}
            className="rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white"
          >
            Create a ticket
          </button>
        </div>
      )}

      {tab === "chat" && (
        <div className="space-y-4">
          {!sessionUuid ? (
            <div className="rounded-2xl border border-slate-200 bg-white p-6">
              <h2 className="text-lg font-semibold">Choose chat language</h2>
              <p className="mt-1 text-sm text-slate-500">
                We will reply in this language. You can change it later without losing the conversation.
              </p>
              <label className="mt-4 block text-sm">
                <span className="mb-1 block text-slate-500">Language</span>
                <select
                  value={chatLocale}
                  onChange={(e) => setChatLocale(normalizeLocale(e.target.value))}
                  className="w-full max-w-xs rounded-xl border border-slate-200 px-3 py-2"
                >
                  {LOCALES.map((loc) => (
                    <option key={loc.code} value={loc.code}>
                      {loc.native} ({loc.name})
                    </option>
                  ))}
                </select>
              </label>
              {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
              <button
                type="button"
                disabled={startSession.isPending}
                onClick={() => startSession.mutate()}
                className="mt-4 rounded-xl bg-teal-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-60"
              >
                {startSession.isPending ? "Starting…" : "Start chat"}
              </button>
            </div>
          ) : (
            <>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <label className="flex items-center gap-2 text-sm">
                  <span className="text-slate-500">Language</span>
                  <select
                    value={chatLocale}
                    disabled={changeLocale.isPending}
                    onChange={(e) => changeLocale.mutate(normalizeLocale(e.target.value))}
                    className="rounded-xl border border-slate-200 px-3 py-1.5"
                  >
                    {LOCALES.map((loc) => (
                      <option key={loc.code} value={loc.code}>
                        {loc.native}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  type="button"
                  onClick={() => {
                    localStorage.removeItem(SESSION_KEY);
                    setSessionUuid(null);
                    setMessages([]);
                    setShowTicketForm(false);
                  }}
                  className="text-sm text-slate-500 hover:text-slate-800"
                >
                  New conversation
                </button>
              </div>

              <div className="flex h-[min(28rem,60vh)] flex-col rounded-2xl border border-slate-200 bg-white">
                <div className="flex-1 space-y-3 overflow-y-auto p-4">
                  {loadMessages.isPending && messages.length === 0 && (
                    <p className="text-sm text-slate-500">Loading conversation…</p>
                  )}
                  {messages.length === 0 && !loadMessages.isPending && (
                    <p className="text-sm text-slate-500">
                      Ask anything about Catalog Studio — subscriptions, marketplaces, labels, and more.
                    </p>
                  )}
                  {messages.map((msg) => {
                    const isUser = msg.role?.toUpperCase() === "USER";
                    return (
                      <div
                        key={msg.uuid}
                        className={`flex ${isUser ? "justify-end" : "justify-start"}`}
                      >
                        <div
                          className={`max-w-[85%] rounded-2xl px-3.5 py-2.5 text-sm whitespace-pre-wrap ${
                            isUser
                              ? "bg-teal-700 text-white"
                              : "bg-slate-100 text-slate-800"
                          }`}
                        >
                          {msg.content}
                        </div>
                      </div>
                    );
                  })}
                  <div ref={bottomRef} />
                </div>
                <form
                  className="flex gap-2 border-t border-slate-100 p-3"
                  onSubmit={(e) => {
                    e.preventDefault();
                    const content = draft.trim();
                    if (!content || sendMessage.isPending) return;
                    sendMessage.mutate(content);
                  }}
                >
                  <input
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    placeholder="Type your message…"
                    className="min-w-0 flex-1 rounded-xl border border-slate-200 px-3 py-2 text-sm"
                    disabled={sendMessage.isPending}
                  />
                  <button
                    type="submit"
                    disabled={sendMessage.isPending || !draft.trim()}
                    className="inline-flex items-center gap-1 rounded-xl bg-teal-700 px-3 py-2 text-sm font-medium text-white disabled:opacity-60"
                  >
                    <Send size={16} />
                    Send
                  </button>
                </form>
              </div>

              <div className="rounded-2xl border border-teal-200 bg-teal-50 px-4 py-3">
                <p className="text-sm text-teal-950">
                  {suggestTicket
                    ? "Still need help? Create a Support Ticket and our team will follow up."
                    : "Prefer a human? Create a Support Ticket anytime."}
                </p>
                <button
                  type="button"
                  onClick={() => setShowTicketForm(true)}
                  className="mt-2 rounded-xl bg-teal-700 px-3 py-1.5 text-sm font-medium text-white"
                >
                  Create a Support Ticket
                </button>
              </div>
            </>
          )}

          {error && <p className="text-sm text-red-600">{error}</p>}

          {showTicketForm && (
            <CreateTicketForm
              sessionUuid={sessionUuid || undefined}
              locale={chatLocale}
              initialSubject={messages.filter((m) => m.role?.toUpperCase() === "USER").at(-1)?.content?.slice(0, 80)}
              initialDescription={messages
                .slice(-6)
                .map((m) => `${m.role}: ${m.content}`)
                .join("\n\n")}
              onCancel={() => setShowTicketForm(false)}
              onCreated={(ticket) => {
                setShowTicketForm(false);
                void qc.invalidateQueries({ queryKey: ["support-tickets"] });
                navigate(`/support/tickets/${ticket.uuid}`);
              }}
            />
          )}
        </div>
      )}
    </div>
  );
}
