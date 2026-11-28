package com.catalogstudio.supportticket;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.supportticket.dto.TicketResponse;
import com.catalogstudio.supportticket.entity.MessageAuthorRole;
import com.catalogstudio.supportticket.entity.SupportTicket;
import com.catalogstudio.supportticket.entity.SupportTicketMessage;
import com.catalogstudio.supportticket.entity.TicketPriority;
import com.catalogstudio.supportticket.entity.TicketStatus;
import com.catalogstudio.supportticket.service.SupportMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupportMapperInternalNotesTest {

    @Test
    void customerDetailExcludesInternalNotes() {
        SupportTicket ticket = SupportTicket.builder()
                .uuid(UUID.randomUUID())
                .ticketNumber("CS-20260101-00002")
                .customerUserId(1L)
                .customerEmail("c@example.com")
                .subject("Subject")
                .description("Desc")
                .priority(TicketPriority.MEDIUM)
                .status(TicketStatus.OPEN)
                .locale("en")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        SupportTicketMessage publicMsg = SupportTicketMessage.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .ticketId(1L)
                .authorRole(MessageAuthorRole.CUSTOMER)
                .body("Public")
                .internal(false)
                .createdAt(Instant.now())
                .build();
        SupportTicketMessage internalMsg = SupportTicketMessage.builder()
                .id(2L)
                .uuid(UUID.randomUUID())
                .ticketId(1L)
                .authorRole(MessageAuthorRole.ADMIN)
                .body("Internal only")
                .internal(true)
                .createdAt(Instant.now())
                .build();

        TicketResponse customerView = SupportMapper.toTicket(
                ticket, List.of(publicMsg, internalMsg), List.of(), null, false, List.of());
        TicketResponse staffView = SupportMapper.toTicket(
                ticket, List.of(publicMsg, internalMsg), List.of(), null, true, List.of());

        assertThat(customerView.messages()).hasSize(1);
        assertThat(customerView.messages().getFirst().body()).isEqualTo("Public");
        assertThat(staffView.messages()).hasSize(2);
        assertThat(staffView.messages().stream().anyMatch(m -> m.internal())).isTrue();
    }
}
