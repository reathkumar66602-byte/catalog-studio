package com.catalogstudio.supportticket.repository;

import com.catalogstudio.supportticket.entity.SupportTicketActivityLog;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketActivityLogRepository extends JpaRepository<SupportTicketActivityLog, Long> {

    List<SupportTicketActivityLog> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

}
