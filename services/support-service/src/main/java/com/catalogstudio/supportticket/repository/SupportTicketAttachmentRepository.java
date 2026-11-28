package com.catalogstudio.supportticket.repository;

import com.catalogstudio.supportticket.entity.SupportTicketAttachment;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;

public interface SupportTicketAttachmentRepository extends JpaRepository<SupportTicketAttachment, Long> {

    Optional<SupportTicketAttachment> findByUuid(UUID uuid);
    List<SupportTicketAttachment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
    List<SupportTicketAttachment> findByUuidInAndCreatedByUserId(Collection<UUID> uuids, Long userId);

}
