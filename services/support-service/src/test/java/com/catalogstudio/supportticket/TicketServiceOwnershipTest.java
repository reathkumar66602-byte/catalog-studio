package com.catalogstudio.supportticket;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.security.AuthUser;
import com.catalogstudio.support.client.UserDirectoryClient;
import com.catalogstudio.supportticket.entity.SupportTicket;
import com.catalogstudio.supportticket.entity.TicketPriority;
import com.catalogstudio.supportticket.entity.TicketStatus;
import com.catalogstudio.supportticket.repository.SupportChatSessionRepository;
import com.catalogstudio.supportticket.repository.SupportSlaConfigurationRepository;
import com.catalogstudio.supportticket.repository.SupportSlaHolidayRepository;
import com.catalogstudio.supportticket.repository.SupportTicketAttachmentRepository;
import com.catalogstudio.supportticket.repository.SupportTicketCategoryRepository;
import com.catalogstudio.supportticket.repository.SupportTicketMessageRepository;
import com.catalogstudio.supportticket.repository.SupportTicketRepository;
import com.catalogstudio.supportticket.service.AttachmentService;
import com.catalogstudio.supportticket.service.SupportEmailNotifier;
import com.catalogstudio.supportticket.service.TicketActivityService;
import com.catalogstudio.supportticket.service.TicketNumberGenerator;
import com.catalogstudio.supportticket.service.TicketService;
import com.catalogstudio.supportticket.sla.SlaCalculator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TicketServiceOwnershipTest {

    @Mock SupportTicketRepository ticketRepository;
    @Mock SupportTicketMessageRepository messageRepository;
    @Mock SupportTicketAttachmentRepository attachmentRepository;
    @Mock SupportTicketCategoryRepository categoryRepository;
    @Mock SupportChatSessionRepository chatSessionRepository;
    @Mock SupportSlaConfigurationRepository slaConfigurationRepository;
    @Mock SupportSlaHolidayRepository slaHolidayRepository;
    @Mock TicketNumberGenerator ticketNumberGenerator;
    @Mock SlaCalculator slaCalculator;
    @Mock TicketActivityService activityService;
    @Mock SupportEmailNotifier emailNotifier;
    @Mock AttachmentService attachmentService;
    @Mock UserDirectoryClient userDirectoryClient;

    @InjectMocks
    TicketService ticketService;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerCannotAccessAnotherUsersTicket() {
        AuthUser customer = AuthUser.fromClaims(10L, UUID.randomUUID(), "a@example.com", "USER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(customer, null, customer.getAuthorities()));

        SupportTicket ticket = SupportTicket.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .ticketNumber("CS-20260101-00001")
                .customerUserId(99L)
                .customerEmail("other@example.com")
                .subject("Help")
                .description("Body")
                .priority(TicketPriority.MEDIUM)
                .status(TicketStatus.OPEN)
                .locale("en")
                .build();
        when(ticketRepository.findByTicketNumberAndDeletedAtIsNull("CS-20260101-00001"))
                .thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> ticketService.getMine("CS-20260101-00001"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot access");
    }
}
