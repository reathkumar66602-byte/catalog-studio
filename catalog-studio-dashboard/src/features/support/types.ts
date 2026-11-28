export type TicketStatus =
  | "OPEN"
  | "ASSIGNED"
  | "IN_PROGRESS"
  | "WAITING_FOR_CUSTOMER"
  | "RESOLVED"
  | "CLOSED"
  | "REOPENED";

export type TicketPriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";

export type AttachmentResponse = {
  uuid: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
};

export type TicketMessageResponse = {
  uuid: string;
  authorUserId?: number | null;
  authorName?: string | null;
  authorRole?: string | null;
  body: string;
  internal: boolean;
  createdAt: string;
  attachments?: AttachmentResponse[];
};

export type TicketActivityResponse = {
  action: string;
  actorName?: string | null;
  fromValue?: string | null;
  toValue?: string | null;
  createdAt: string;
  metadata?: string | null;
};

export type TicketSummaryResponse = {
  uuid: string;
  ticketNumber: string;
  subject: string;
  categoryCode?: string | null;
  priority: TicketPriority | string;
  status: TicketStatus | string;
  customerName?: string | null;
  customerEmail?: string | null;
  assignedAdminUserId?: number | null;
  assignedAdminName?: string | null;
  slaDeadlineAt?: string | null;
  slaBreached: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TicketResponse = {
  uuid: string;
  ticketNumber: string;
  customerUserId?: number | null;
  customerEmail?: string | null;
  customerName?: string | null;
  subject: string;
  description: string;
  categoryCode?: string | null;
  categoryName?: string | null;
  priority: TicketPriority | string;
  status: TicketStatus | string;
  locale?: string | null;
  assignedAdminUserId?: number | null;
  assignedAdminName?: string | null;
  chatSessionUuid?: string | null;
  slaDeadlineAt?: string | null;
  slaBreached: boolean;
  firstResponseAt?: string | null;
  resolvedAt?: string | null;
  closedAt?: string | null;
  resolutionSummary?: string | null;
  createdAt: string;
  updatedAt: string;
  messages: TicketMessageResponse[];
  attachments: AttachmentResponse[];
  activities?: TicketActivityResponse[];
};

export type CategoryResponse = {
  uuid: string;
  code: string;
  name: string;
  description?: string | null;
  active: boolean;
  sortOrder: number;
  createdAt?: string;
  updatedAt?: string;
};

export type ChatSessionResponse = {
  uuid: string;
  locale: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type ChatMessageResponse = {
  uuid: string;
  role: string;
  content: string;
  suggestTicket: boolean;
  createdAt: string;
};

export type AdminStatsResponse = {
  totalCount: number;
  openCount: number;
  assignedCount: number;
  inProgressCount: number;
  waitingForCustomerCount: number;
  resolvedCount: number;
  closedCount: number;
  unassignedCount: number;
  slaBreachedCount: number;
};

export type PageResult<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
};

export type CreateTicketPayload = {
  sessionUuid?: string;
  subject: string;
  description: string;
  categoryCode?: string;
  priority?: string;
  locale?: string;
  attachmentIds?: string[];
};

export const TICKET_STATUSES: TicketStatus[] = [
  "OPEN",
  "ASSIGNED",
  "IN_PROGRESS",
  "WAITING_FOR_CUSTOMER",
  "RESOLVED",
  "CLOSED",
  "REOPENED",
];

export const TICKET_PRIORITIES: TicketPriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];

export const FALLBACK_CATEGORY_CODES = [
  "ACCOUNT",
  "PRODUCTS",
  "MARKETPLACE",
  "ORDERS",
  "BILLING",
  "TECHNICAL",
  "OTHER",
] as const;

export function formatTicketStatus(status: string) {
  return status.replaceAll("_", " ");
}

export function priorityTone(priority: string) {
  const p = priority.toUpperCase();
  if (p === "URGENT") return "bg-rose-100 text-rose-800";
  if (p === "HIGH") return "bg-amber-100 text-amber-900";
  if (p === "MEDIUM") return "bg-sky-100 text-sky-800";
  return "bg-slate-100 text-slate-700";
}

export function statusTone(status: string) {
  const s = status.toUpperCase();
  if (s === "CLOSED" || s === "RESOLVED") return "bg-emerald-100 text-emerald-800";
  if (s === "WAITING_FOR_CUSTOMER") return "bg-amber-100 text-amber-900";
  if (s === "REOPENED" || s === "OPEN") return "bg-teal-100 text-teal-900";
  return "bg-slate-100 text-slate-700";
}
