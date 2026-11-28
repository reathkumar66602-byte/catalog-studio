package com.catalogstudio.supportticket.repository;

import com.catalogstudio.supportticket.entity.SupportTicketMessage;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, Long> {

    List<SupportTicketMessage> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    List<SupportTicketMessage> findByTicketIdAndInternalFalseOrderByCreatedAtAsc(Long ticketId);

}
