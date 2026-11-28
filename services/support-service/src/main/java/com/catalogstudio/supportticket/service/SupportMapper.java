package com.catalogstudio.supportticket.service;

import com.catalogstudio.supportticket.dto.*;
import com.catalogstudio.supportticket.entity.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SupportMapper {

    private SupportMapper() {}

    public static CategoryResponse toCategory(SupportTicketCategory c) {
        return new CategoryResponse(
                c.getUuid(), c.getCode(), c.getName(), c.getDescription(),
                c.isActive(), c.getSortOrder(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public static AttachmentResponse toAttachment(SupportTicketAttachment a) {
        return new AttachmentResponse(
                a.getUuid(), a.getOriginalName(), a.getContentType(), a.getSizeBytes(), a.getCreatedAt());
    }

    public static TicketMessageResponse toMessage(
            SupportTicketMessage m, List<SupportTicketAttachment> attachments) {
        List<AttachmentResponse> atts = attachments == null ? List.of()
                : attachments.stream().map(SupportMapper::toAttachment).toList();
        return new TicketMessageResponse(
                m.getUuid(), m.getAuthorUserId(), m.getAuthorName(),
                m.getAuthorRole().name(), m.getBody(), m.isInternal(), m.getCreatedAt(), atts);
    }

    public static TicketSummaryResponse toSummary(SupportTicket t) {
        return new TicketSummaryResponse(
                t.getUuid(),
                t.getTicketNumber(),
                t.getSubject(),
                t.getCategory() == null ? null : t.getCategory().getCode(),
                t.getPriority().name(),
                t.getStatus().name(),
                t.getCustomerName(),
                t.getCustomerEmail(),
                t.getAssignedAdminUserId(),
                t.getAssignedAdminName(),
                t.getSlaDeadlineAt(),
                isSlaBreached(t),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    public static TicketActivityResponse toActivity(SupportTicketActivityLog log) {
        return new TicketActivityResponse(
                log.getAction(),
                log.getActorName(),
                log.getFromValue(),
                log.getToValue(),
                log.getCreatedAt(),
                log.getMetadata());
    }

    public static TicketResponse toTicket(
            SupportTicket t,
            List<SupportTicketMessage> messages,
            List<SupportTicketAttachment> attachments,
            UUID chatSessionUuid,
            boolean includeInternal,
            List<TicketActivityResponse> activities) {
        Map<Long, List<SupportTicketAttachment>> byMessage = attachments.stream()
                .filter(a -> a.getMessageId() != null)
                .collect(Collectors.groupingBy(SupportTicketAttachment::getMessageId));
        List<TicketMessageResponse> msgResponses = messages.stream()
                .filter(m -> includeInternal || !m.isInternal())
                .map(m -> toMessage(m, byMessage.getOrDefault(m.getId(), List.of())))
                .toList();
        List<AttachmentResponse> ticketAtts = attachments.stream()
                .filter(a -> a.getMessageId() == null)
                .map(SupportMapper::toAttachment)
                .toList();
        return new TicketResponse(
                t.getUuid(),
                t.getTicketNumber(),
                t.getCustomerUserId(),
                t.getCustomerEmail(),
                t.getCustomerName(),
                t.getSubject(),
                t.getDescription(),
                t.getCategory() == null ? null : t.getCategory().getCode(),
                t.getCategory() == null ? null : t.getCategory().getName(),
                t.getPriority().name(),
                t.getStatus().name(),
                t.getLocale(),
                t.getAssignedAdminUserId(),
                t.getAssignedAdminName(),
                chatSessionUuid,
                t.getSlaDeadlineAt(),
                isSlaBreached(t),
                t.getFirstResponseAt(),
                t.getResolvedAt(),
                t.getClosedAt(),
                t.getResolutionSummary(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                msgResponses,
                ticketAtts,
                activities == null ? List.of() : activities);
    }

    public static boolean isSlaBreached(SupportTicket t) {
        if (t.getSlaBreachedAt() != null) {
            return true;
        }
        if (t.getSlaDeadlineAt() == null || t.getStatus().isTerminal()) {
            return false;
        }
        return t.getSlaDeadlineAt().isBefore(Instant.now());
    }

    public static ChatSessionResponse toChatSession(SupportChatSession s) {
        return new ChatSessionResponse(
                s.getUuid(), s.getLocale(), s.getStatus().name(), s.getCreatedAt(), s.getUpdatedAt());
    }

    public static ChatMessageResponse toChatMessage(SupportChatMessage m, boolean suggestTicket) {
        return new ChatMessageResponse(
                m.getUuid(), m.getRole().name(), m.getContent(), suggestTicket, m.getCreatedAt());
    }

    public static KnowledgeArticleResponse toKnowledge(SupportKnowledgeArticle a) {
        return new KnowledgeArticleResponse(
                a.getUuid(), a.getSlug(), a.getTitle(), a.getSummary(), a.getBody(),
                a.getLocale(), a.getCategory(), a.getTags(), a.isActive(), a.isVerified(),
                a.getSortOrder(), a.getCreatedAt(), a.getUpdatedAt());
    }

    public static SlaConfigResponse toSla(SupportSlaConfiguration c, List<SupportSlaHoliday> holidays) {
        List<SlaConfigResponse.Holiday> hs = holidays == null ? List.of()
                : holidays.stream()
                        .map(h -> new SlaConfigResponse.Holiday(h.getHolidayDate(), h.getName()))
                        .toList();
        return new SlaConfigResponse(
                c.getUuid(), c.getName(), c.getTimezone(), c.getWorkingDays(),
                c.getWorkStart(), c.getWorkEnd(), c.getInitialResponseBusinessDays(),
                c.getWarnHoursBefore(), c.isActive(), hs, c.getCreatedAt(), c.getUpdatedAt());
    }
}
