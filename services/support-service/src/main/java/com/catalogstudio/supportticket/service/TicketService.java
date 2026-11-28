package com.catalogstudio.supportticket.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.security.AuthUser;
import com.catalogstudio.security.Roles;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.support.client.UserDirectoryClient;
import com.catalogstudio.support.client.UserSnapshot;
import com.catalogstudio.supportticket.SupportedLocales;
import com.catalogstudio.supportticket.dto.CreateTicketRequest;
import com.catalogstudio.supportticket.dto.TicketReplyRequest;
import com.catalogstudio.supportticket.dto.TicketResponse;
import com.catalogstudio.supportticket.dto.TicketSummaryResponse;
import com.catalogstudio.supportticket.entity.MessageAuthorRole;
import com.catalogstudio.supportticket.entity.SupportChatSession;
import com.catalogstudio.supportticket.entity.SupportSlaConfiguration;
import com.catalogstudio.supportticket.entity.SupportSlaHoliday;
import com.catalogstudio.supportticket.entity.SupportTicket;
import com.catalogstudio.supportticket.entity.SupportTicketAttachment;
import com.catalogstudio.supportticket.entity.SupportTicketCategory;
import com.catalogstudio.supportticket.entity.SupportTicketMessage;
import com.catalogstudio.supportticket.entity.TicketPriority;
import com.catalogstudio.supportticket.entity.TicketStatus;
import com.catalogstudio.supportticket.repository.SupportChatSessionRepository;
import com.catalogstudio.supportticket.repository.SupportSlaConfigurationRepository;
import com.catalogstudio.supportticket.repository.SupportSlaHolidayRepository;
import com.catalogstudio.supportticket.repository.SupportTicketAttachmentRepository;
import com.catalogstudio.supportticket.repository.SupportTicketCategoryRepository;
import com.catalogstudio.supportticket.repository.SupportTicketMessageRepository;
import com.catalogstudio.supportticket.repository.SupportTicketRepository;
import com.catalogstudio.supportticket.sla.SlaCalculator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final SupportTicketAttachmentRepository attachmentRepository;
    private final SupportTicketCategoryRepository categoryRepository;
    private final SupportChatSessionRepository chatSessionRepository;
    private final SupportSlaConfigurationRepository slaConfigurationRepository;
    private final SupportSlaHolidayRepository slaHolidayRepository;
    private final TicketNumberGenerator ticketNumberGenerator;
    private final SlaCalculator slaCalculator;
    private final TicketActivityService activityService;
    private final SupportEmailNotifier emailNotifier;
    private final AttachmentService attachmentService;
    private final UserDirectoryClient userDirectoryClient;

    @Transactional
    public TicketResponse create(CreateTicketRequest request) {
        AuthUser user = SecurityUtils.currentUser();
        if (Roles.isStaff(user.role()) && !Roles.isWorkspaceUser(user.role())) {
            // staff may still create on behalf of themselves for testing; ownership is their user id
        }
        UserSnapshot profile = userDirectoryClient.findById(user.id()).orElse(null);
        String email = profile != null && StringUtils.hasText(profile.email()) ? profile.email() : user.email();
        String name = profile != null && StringUtils.hasText(profile.name()) ? profile.name() : null;
        if (!StringUtils.hasText(email)) {
            throw ApiException.badRequest("Customer email is required to create a ticket");
        }

        SupportTicketCategory category = null;
        if (StringUtils.hasText(request.categoryCode())) {
            category = categoryRepository.findByCodeIgnoreCase(request.categoryCode().trim())
                    .orElseThrow(() -> ApiException.badRequest("Unknown category code"));
        }

        Long chatSessionId = null;
        if (request.sessionUuid() != null) {
            SupportChatSession session = chatSessionRepository
                    .findByUuidAndCustomerUserId(request.sessionUuid(), user.id())
                    .orElseThrow(() -> ApiException.notFound("Chat session not found"));
            chatSessionId = session.getId();
        }

        SupportSlaConfiguration sla = slaConfigurationRepository.findFirstByActiveTrueOrderByIdAsc().orElse(null);
        List<SupportSlaHoliday> holidays = sla == null
                ? List.of()
                : slaHolidayRepository.findBySlaConfigIdOrderByHolidayDateAsc(sla.getId());
        Instant now = Instant.now();

        SupportTicket ticket = SupportTicket.builder()
                .ticketNumber(ticketNumberGenerator.next())
                .customerUserId(user.id())
                .customerEmail(email.trim())
                .customerName(name)
                .subject(request.subject().trim())
                .description(request.description().trim())
                .category(category)
                .priority(TicketPriority.from(request.priority()))
                .status(TicketStatus.OPEN)
                .locale(SupportedLocales.normalize(
                        StringUtils.hasText(request.locale())
                                ? request.locale()
                                : (profile == null ? null : profile.preferredLocale())))
                .chatSessionId(chatSessionId)
                .slaDeadlineAt(slaCalculator.computeDeadline(now, sla, holidays))
                .build();
        ticket = ticketRepository.save(ticket);

        SupportTicketMessage initial = messageRepository.save(SupportTicketMessage.builder()
                .ticketId(ticket.getId())
                .authorUserId(user.id())
                .authorName(name)
                .authorRole(MessageAuthorRole.CUSTOMER)
                .body(request.description().trim())
                .internal(false)
                .build());

        List<SupportTicketAttachment> attachments =
                attachmentService.claimPending(request.attachmentIds(), user.id(), ticket.getId());
        for (SupportTicketAttachment attachment : attachments) {
            attachment.setMessageId(initial.getId());
        }
        if (!attachments.isEmpty()) {
            attachmentRepository.saveAll(attachments);
        }

        activityService.log(ticket.getId(), user.id(), name, "CREATED", null, TicketStatus.OPEN.name(), null);
        emailNotifier.notify(ticket, "TICKET_CREATED", "support-ticket-created", Map.of());

        return detail(ticket, false);
    }

    @Transactional(readOnly = true)
    public Page<TicketSummaryResponse> listMine(String status, String q, Pageable pageable) {
        AuthUser user = SecurityUtils.currentUser();
        TicketStatus statusEnum = null;
        if (StringUtils.hasText(status)) {
            try {
                statusEnum = TicketStatus.from(status);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("Invalid status");
            }
        }
        String query = StringUtils.hasText(q) ? q.trim() : null;
        return ticketRepository.searchCustomerTickets(user.id(), statusEnum, query, pageable)
                .map(SupportMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public TicketResponse getMine(String key) {
        AuthUser user = SecurityUtils.currentUser();
        SupportTicket ticket = resolveAccessible(key, user, false);
        return detail(ticket, false);
    }

    @Transactional
    public TicketResponse reply(String key, TicketReplyRequest request) {
        AuthUser user = SecurityUtils.currentUser();
        SupportTicket ticket = resolveAccessible(key, user, false);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw ApiException.badRequest("Closed tickets must be reopened before replying");
        }
        SupportTicketMessage message = messageRepository.save(SupportTicketMessage.builder()
                .ticketId(ticket.getId())
                .authorUserId(user.id())
                .authorName(ticket.getCustomerName())
                .authorRole(MessageAuthorRole.CUSTOMER)
                .body(request.body().trim())
                .internal(false)
                .build());
        attachmentService.claimPending(request.attachmentIds(), user.id(), ticket.getId())
                .forEach(a -> a.setMessageId(message.getId()));

        if (ticket.getStatus() == TicketStatus.WAITING_FOR_CUSTOMER
                || ticket.getStatus() == TicketStatus.RESOLVED) {
            TicketStatus previous = ticket.getStatus();
            ticket.setStatus(TicketStatus.IN_PROGRESS);
            activityService.log(ticket.getId(), user.id(), ticket.getCustomerName(),
                    "STATUS_CHANGED", previous.name(), TicketStatus.IN_PROGRESS.name(), null);
        }
        ticketRepository.save(ticket);
        emailNotifier.notify(ticket, "CUSTOMER_REPLIED", "support-ticket-customer-reply", Map.of(
                "replyBody", request.body().trim()));
        return detail(ticket, false);
    }

    @Transactional
    public TicketResponse reopen(String key) {
        AuthUser user = SecurityUtils.currentUser();
        SupportTicket ticket = resolveAccessible(key, user, false);
        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
            throw ApiException.badRequest("Only resolved or closed tickets can be reopened");
        }
        TicketStatus previous = ticket.getStatus();
        ticket.setStatus(TicketStatus.REOPENED);
        ticket.setResolvedAt(null);
        ticket.setClosedAt(null);
        ticket.setResolutionSummary(null);
        SupportSlaConfiguration sla = slaConfigurationRepository.findFirstByActiveTrueOrderByIdAsc().orElse(null);
        List<SupportSlaHoliday> holidays = sla == null
                ? List.of()
                : slaHolidayRepository.findBySlaConfigIdOrderByHolidayDateAsc(sla.getId());
        ticket.setSlaDeadlineAt(slaCalculator.computeDeadline(Instant.now(), sla, holidays));
        ticket.setSlaWarnedAt(null);
        ticket.setSlaBreachedAt(null);
        ticketRepository.save(ticket);
        activityService.log(ticket.getId(), user.id(), ticket.getCustomerName(),
                "REOPENED", previous.name(), TicketStatus.REOPENED.name(), null);
        emailNotifier.notify(ticket, "TICKET_REOPENED", "support-ticket-reopened", Map.of());
        return detail(ticket, false);
    }

    SupportTicket resolveAccessible(String key, AuthUser user, boolean staffOk) {
        SupportTicket ticket = resolve(key);
        if (Roles.isStaff(user.role()) && staffOk) {
            return ticket;
        }
        if (!ticket.getCustomerUserId().equals(user.id())) {
            throw ApiException.forbidden("You cannot access this ticket");
        }
        return ticket;
    }

    SupportTicket resolve(String key) {
        if (key == null || key.isBlank()) {
            throw ApiException.badRequest("Ticket id is required");
        }
        try {
            UUID uuid = UUID.fromString(key);
            return ticketRepository.findByUuidAndDeletedAtIsNull(uuid)
                    .orElseThrow(() -> ApiException.notFound("Ticket not found"));
        } catch (IllegalArgumentException ignored) {
            return ticketRepository.findByTicketNumberAndDeletedAtIsNull(key.trim())
                    .orElseThrow(() -> ApiException.notFound("Ticket not found"));
        }
    }

    TicketResponse detail(SupportTicket ticket, boolean includeInternal) {
        List<SupportTicketMessage> messages = includeInternal
                ? messageRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId())
                : messageRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(ticket.getId());
        List<SupportTicketAttachment> attachments =
                attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        UUID chatUuid = null;
        if (ticket.getChatSessionId() != null) {
            chatUuid = chatSessionRepository.findById(ticket.getChatSessionId())
                    .map(SupportChatSession::getUuid)
                    .orElse(null);
        }
        return SupportMapper.toTicket(
                ticket, messages, attachments, chatUuid, includeInternal, activityService.list(ticket.getId()));
    }
}
