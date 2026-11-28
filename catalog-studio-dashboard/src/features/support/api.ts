import { api } from "../../api/client";
import type {
  AdminStatsResponse,
  AttachmentResponse,
  CategoryResponse,
  ChatMessageResponse,
  ChatSessionResponse,
  CreateTicketPayload,
  PageResult,
  TicketResponse,
  TicketSummaryResponse,
} from "./types";

function data<T>(promise: Promise<{ data: { data: T } }>) {
  return promise.then((res) => res.data.data);
}

/** Numeric user id from JWT `userId` claim (MeResponse only exposes UUID). */
export function currentNumericUserId(): number | null {
  const token = localStorage.getItem("cs_access");
  if (!token) return null;
  try {
    const payload = JSON.parse(atob(token.split(".")[1] || "")) as { userId?: number | string };
    const id = Number(payload.userId);
    return Number.isFinite(id) ? id : null;
  } catch {
    return null;
  }
}

export async function downloadAttachment(uuid: string, filename?: string) {
  const res = await api.get(`/support/attachments/${uuid}/download`, { responseType: "blob" });
  const blob = res.data as Blob;
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename || "attachment";
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export const supportApi = {
  createChatSession: (locale?: string): Promise<ChatSessionResponse> =>
    data(api.post("/support/chat/sessions", { locale })),

  listChatMessages: (sessionUuid: string): Promise<ChatMessageResponse[]> =>
    data(api.get(`/support/chat/sessions/${sessionUuid}/messages`)),

  postChatMessage: (sessionUuid: string, content: string): Promise<ChatMessageResponse> =>
    data(api.post(`/support/chat/sessions/${sessionUuid}/messages`, { content })),

  updateChatLocale: (sessionUuid: string, locale: string): Promise<ChatSessionResponse> =>
    data(api.patch(`/support/chat/sessions/${sessionUuid}/locale`, { locale })),

  listCategories: () =>
    data(api.get("/support/categories")) as Promise<CategoryResponse[]>,

  listTickets: (params: { status?: string; q?: string; page?: number; size?: number; sort?: string }) =>
    data(api.get("/support/tickets", { params })) as Promise<PageResult<TicketSummaryResponse>>,

  getTicket: (id: string) =>
    data(api.get(`/support/tickets/${id}`)) as Promise<TicketResponse>,

  createTicket: (payload: CreateTicketPayload) =>
    data(api.post("/support/tickets", payload)) as Promise<TicketResponse>,

  replyTicket: (id: string, body: string, attachmentIds?: string[]) =>
    data(api.post(`/support/tickets/${id}/replies`, { body, attachmentIds })) as Promise<TicketResponse>,

  reopenTicket: (id: string) =>
    data(api.post(`/support/tickets/${id}/reopen`)) as Promise<TicketResponse>,

  uploadPendingAttachment: async (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return data(api.post("/support/attachments", form)) as Promise<AttachmentResponse>;
  },

  uploadTicketAttachment: async (id: string, file: File) => {
    const form = new FormData();
    form.append("file", file);
    return data(api.post(`/support/tickets/${id}/attachments`, form)) as Promise<AttachmentResponse>;
  },

  adminStats: () =>
    data(api.get("/support/admin/stats")) as Promise<AdminStatsResponse>,

  adminTickets: (params: Record<string, string | number | boolean | undefined>) =>
    data(api.get("/support/admin/tickets", { params })) as Promise<PageResult<TicketSummaryResponse>>,

  adminTicket: (id: string) =>
    data(api.get(`/support/admin/tickets/${id}`)) as Promise<TicketResponse>,

  assignTicket: (id: string, adminUserId?: number | null, adminName?: string) =>
    data(api.post(`/support/admin/tickets/${id}/assign`, {
      adminUserId: adminUserId ?? undefined,
      adminName,
    })) as Promise<TicketResponse>,

  updatePriority: (id: string, priority: string) =>
    data(api.patch(`/support/admin/tickets/${id}/priority`, { priority })) as Promise<TicketResponse>,

  updateStatus: (id: string, status: string) =>
    data(api.patch(`/support/admin/tickets/${id}/status`, { status })) as Promise<TicketResponse>,

  adminReply: (
    id: string,
    payload: { body: string; internal?: boolean; status?: string; attachmentIds?: string[] },
  ) => data(api.post(`/support/admin/tickets/${id}/replies`, payload)) as Promise<TicketResponse>,

  closeTicket: (id: string, resolutionSummary?: string) =>
    data(api.post(`/support/admin/tickets/${id}/close`, { resolutionSummary })) as Promise<TicketResponse>,

  adminCategories: () =>
    data(api.get("/support/admin/categories")) as Promise<CategoryResponse[]>,

  createCategory: (payload: {
    code: string;
    name: string;
    description?: string;
    active?: boolean;
    sortOrder?: number;
  }) => data(api.post("/support/admin/categories", payload)) as Promise<CategoryResponse>,

  updateCategory: (
    uuid: string,
    payload: {
      code: string;
      name: string;
      description?: string;
      active?: boolean;
      sortOrder?: number;
    },
  ) => data(api.put(`/support/admin/categories/${uuid}`, payload)) as Promise<CategoryResponse>,

  deleteCategory: (uuid: string) => data(api.delete(`/support/admin/categories/${uuid}`)),

  listKnowledge: async () => {
    const page = (await data(api.get("/support/admin/knowledge", { params: { size: 200 } }))) as PageResult<{
      uuid: string;
      slug: string;
      title: string;
      summary?: string;
      body: string;
      locale: string;
      category?: string;
      tags?: string;
      active: boolean;
      verified: boolean;
      sortOrder: number;
    }>;
    return page?.content ?? [];
  },

  saveKnowledge: (
    payload: {
      slug: string;
      title: string;
      summary?: string;
      body: string;
      locale?: string;
      category?: string;
      tags?: string;
      active?: boolean;
      verified?: boolean;
      sortOrder?: number;
    },
    uuid?: string,
  ) =>
    uuid
      ? data(api.put(`/support/admin/knowledge/${uuid}`, payload))
      : data(api.post("/support/admin/knowledge", payload)),

  deleteKnowledge: (uuid: string) => data(api.delete(`/support/admin/knowledge/${uuid}`)),

  getSla: () =>
    data(api.get("/support/admin/sla")) as Promise<{
      uuid: string;
      name: string;
      timezone: string;
      workingDays: string;
      workStart: string;
      workEnd: string;
      initialResponseBusinessDays: number;
      warnHoursBefore: number;
      active: boolean;
      holidays: Array<{ holidayDate: string; name: string }>;
    }>,

  saveSla: (payload: {
    name: string;
    timezone: string;
    workingDays: string;
    workStart: string;
    workEnd: string;
    initialResponseBusinessDays: number;
    warnHoursBefore: number;
    active?: boolean;
    holidays?: Array<{ holidayDate: string; name: string }>;
  }) => data(api.put("/support/admin/sla", payload)),
};
