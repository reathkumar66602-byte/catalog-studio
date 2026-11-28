package com.catalogstudio.supportticket.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.security.AuthUser;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.support.client.UserDirectoryClient;
import com.catalogstudio.support.client.UserSnapshot;
import com.catalogstudio.supportticket.dto.AdminStatsResponse;
import com.catalogstudio.supportticket.dto.AssignTicketRequest;
import com.catalogstudio.supportticket.dto.CloseTicketRequest;
import com.catalogstudio.supportticket.dto.TicketReplyRequest;
import com.catalogstudio.supportticket.dto.TicketResponse;
import com.catalogstudio.supportticket.dto.TicketSummaryResponse;
import com.catalogstudio.supportticket.dto.UpdatePriorityRequest;
import com.catalogstudio.supportticket.dto.UpdateStatusRequest;
import com.catalogstudio.supportticket.entity.MessageAuthorRole;
import com.catalogstudio.supportticket.entity.SupportTicket;
import com.catalogstudio.supportticket.entity.SupportTicketMessage;
import com.catalogstudio.supportticket.entity.TicketPriority;
import com.catalogstudio.supportticket.entity.TicketStatus;
import com.catalogstudio.supportticket.repository.SupportTicketCategoryRepository;
import com.catalogstudio.supportticket.repository.SupportTicketMessageRepository;
import com.catalogstudio.supportticket.repository.SupportTicketRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminTicketService {

    private static final Set<TicketStatus> OPENISH = EnumSet.of(
            TicketStatus.OPEN, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS,
            TicketStatus.WAITING_FOR_CUSTOMER, TicketStatus.REOPENED);

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final SupportTicketCategoryRepository categoryRepository;
    private final TicketService ticketService;
    private final TicketActivityService activityService;
    private final SupportEmailNotifier emailNotifier;
    private final AttachmentService attachmentService;
    private final UserDirectoryClient userDirectoryClient;

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        Instant now = Instant.now();
        return new AdminStatsResponse(
                ticketRepository.countByDeletedAtIsNull(),
                ticketRepository.countByDeletedAtIsNullAndStatus(TicketStatus.OPEN),
                ticketRepository.countByDeletedAtIsNullAndStatus(TicketStatus.ASSIGNED),
                ticketRepository.countByDeletedAtIsNullAndStatus(TicketStatus.IN_PROGRESS),
                ticketRepository.countByDeletedAtIsNullAndStatus(TicketStatus.WAITING_FOR_CUSTOMER),
                ticketRepository.countByDeletedAtIsNullAndStatus(TicketStatus.RESOLVED),
                ticketRepository.countByDeletedAtIsNullAndStatus(TicketStatus.CLOSED),
                ticketRepository.countByDeletedAtIsNullAndAssignedAdminUserIdIsNullAndStatusIn(OPENISH),
                ticketRepository.countSlaBreached(now));
    }

    @Transactional(readOnly = true)
    public Page<TicketSummaryResponse> search(
            String status,
            String priority,
            String categoryCode,
            Long assigneeId,
            Instant from,
            Instant to,
            Boolean slaBreached,
            String q,
            Pageable pageable) {
        TicketStatus statusEnum = null;
        if (StringUtils.hasText(status) && !"ALL".equalsIgnoreCase(status.trim())) {
            statusEnum = TicketStatus.from(status);
        }
        TicketPriority priorityEnum = StringUtils.hasText(priority) ? TicketPriority.from(priority) : null;
        Long categoryId = null;
        if (StringUtils.hasText(categoryCode)) {
            categoryId = categoryRepository.findByCodeIgnoreCase(categoryCode.trim())
                    .map(c -> c.getId())
                    .orElseThrow(() -> ApiException.badRequest("Unknown category code"));
        }
        return ticketRepository.searchAdminTickets(
                        statusEnum,
                        priorityEnum,
                        categoryId,
                        assigneeId,
                        from,
                        to,
                        Boolean.TRUE.equals(slaBreached),
                        Instant.now(),
                        StringUtils.hasText(q) ? q.trim() : null,
                        pageable)
                .map(SupportMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public TicketResponse get(String key) {
        return ticketService.detail(ticketService.resolve(key), true);
    }

    @Transactional
    public TicketResponse assign(String key, AssignTicketRequest request) {
        AuthUser actor = SecurityUtils.currentUser();
        SupportTicket ticket = ticketService.resolve(key);
        Long adminUserId = request.adminUserId() != null ? request.adminUserId() : actor.id();
        String adminName = request.adminName();
        if (!StringUtils.hasText(adminName)) {
            adminName = userDirectoryClient.findById(adminUserId)
                    .map(UserSnapshot::name)
                    .orElseGet(() -> StringUtils.hasText(actor.email()) ? actor.email() : "Admin");
        }
        Long previous = ticket.getAssignedAdminUserId();
        ticket.setAssignedAdminUserId(adminUserId);
        ticket.setAssignedAdminName(adminName);
        if (ticket.getStatus() == TicketStatus.OPEN || ticket.getStatus() == TicketStatus.REOPENED) {
            ticket.setStatus(TicketStatus.ASSIGNED);
        }
        ticketRepository.save(ticket);
        activityService.log(ticket.getId(), actor.id(), actor.email(), "ASSIGNED",
                previous == null ? null : String.valueOf(previous),
                String.valueOf(adminUserId), null);
        emailNotifier.notify(ticket, "TICKET_ASSIGNED", "support-ticket-assigned", Map.of(
                "assignedTo", adminName));
        return ticketService.detail(ticket, true);
    }

    @Transactional
    public TicketResponse updatePriority(String key, UpdatePriorityRequest request) {
        AuthUser actor = SecurityUtils.currentUser();
        SupportTicket ticket = ticketService.resolve(key);
        TicketPriority previous = ticket.getPriority();
        TicketPriority next = TicketPriority.from(request.priority());
        ticket.setPriority(next);
        ticketRepository.save(ticket);
        activityService.log(ticket.getId(), actor.id(), actor.email(), "PRIORITY_CHANGED",
                previous.name(), next.name(), null);
        emailNotifier.notify(ticket, "STATUS_CHANGED", "support-ticket-status-changed", Map.of(
                "message", "Priority updated to " + next.name()));
        return ticketService.detail(ticket, true);
    }

    @Transactional
    public TicketResponse updateStatus(String key, UpdateStatusRequest request) {
        AuthUser actor = SecurityUtils.currentUser();
        SupportTicket ticket = ticketService.resolve(key);
        TicketStatus previous = ticket.getStatus();
        TicketStatus next = TicketStatus.from(request.status());
        applyStatusSideEffects(ticket, next);
        ticket.setStatus(next);
        ticketRepository.save(ticket);
        activityService.log(ticket.getId(), actor.id(), actor.email(), "STATUS_CHANGED",
                previous.name(), next.name(), null);
        if (next == TicketStatus.RESOLVED) {
            emailNotifier.notify(ticket, "TICKET_RESOLVED", "support-ticket-resolved", Map.of(
                    "resolutionSummary", ticket.getResolutionSummary() == null ? "" : ticket.getResolutionSummary()));
        } else {
            emailNotifier.notify(ticket, "STATUS_CHANGED", "support-ticket-status-changed", Map.of(
                    "message", "Status updated to " + next.name()));
        }
        return ticketService.detail(ticket, true);
    }

    @Transactional
    public TicketResponse reply(String key, TicketReplyRequest request) {
        AuthUser actor = SecurityUtils.currentUser();
        SupportTicket ticket = ticketService.resolve(key);
        boolean internal = Boolean.TRUE.equals(request.internal());
        SupportTicketMessage message = messageRepository.save(SupportTicketMessage.builder()
                .ticketId(ticket.getId())
                .authorUserId(actor.id())
                .authorName(StringUtils.hasText(actor.email()) ? actor.email() : "Support")
                .authorRole(MessageAuthorRole.ADMIN)
                .body(request.body().trim())
                .internal(internal)
                .build());
        attachmentService.claimPending(request.attachmentIds(), actor.id(), ticket.getId())
                .forEach(a -> a.setMessageId(message.getId()));

        if (!internal && ticket.getFirstResponseAt() == null) {
            ticket.setFirstResponseAt(Instant.now());
        }
        if (StringUtils.hasText(request.status())) {
            TicketStatus previous = ticket.getStatus();
            TicketStatus next = TicketStatus.from(request.status());
            applyStatusSideEffects(ticket, next);
            ticket.setStatus(next);
            activityService.log(ticket.getId(), actor.id(), actor.email(), "STATUS_CHANGED",
                    previous.name(), next.name(), "via reply");
        } else if (!internal && ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }
        ticketRepository.save(ticket);
        if (!internal) {
            emailNotifier.notify(ticket, "ADMIN_REPLIED", "support-ticket-admin-reply", Map.of(
                    "replyBody", request.body().trim()));
        }
        return ticketService.detail(ticket, true);
    }

    @Transactional
    public TicketResponse close(String key, CloseTicketRequest request) {
        AuthUser actor = SecurityUtils.currentUser();
        SupportTicket ticket = ticketService.resolve(key);
        TicketStatus previous = ticket.getStatus();
        ticket.setStatus(TicketStatus.CLOSED);
        ticket.setClosedAt(Instant.now());
        if (ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(ticket.getClosedAt());
        }
        if (request != null && StringUtils.hasText(request.resolutionSummary())) {
            ticket.setResolutionSummary(request.resolutionSummary().trim());
        }
        ticketRepository.save(ticket);
        activityService.log(ticket.getId(), actor.id(), actor.email(), "CLOSED",
                previous.name(), TicketStatus.CLOSED.name(), null);
        emailNotifier.notify(ticket, "TICKET_CLOSED", "support-ticket-closed", Map.of(
                "resolutionSummary", ticket.getResolutionSummary() == null ? "" : ticket.getResolutionSummary()));
        return ticketService.detail(ticket, true);
    }

    private void applyStatusSideEffects(SupportTicket ticket, TicketStatus next) {
        Instant now = Instant.now();
        if (next == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(now);
        } else if (next == TicketStatus.CLOSED) {
            ticket.setClosedAt(now);
            if (ticket.getResolvedAt() == null) {
                ticket.setResolvedAt(now);
            }
        } else if (next == TicketStatus.REOPENED) {
            ticket.setResolvedAt(null);
            ticket.setClosedAt(null);
        }
    }
}
