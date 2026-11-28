package com.catalogstudio.supportticket.repository;

import com.catalogstudio.supportticket.entity.SupportTicketCategory;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportTicketCategoryRepository extends JpaRepository<SupportTicketCategory, Long> {

    Optional<SupportTicketCategory> findByCodeIgnoreCase(String code);
    Optional<SupportTicketCategory> findByUuid(UUID uuid);
    List<SupportTicketCategory> findAllByOrderBySortOrderAsc();
    List<SupportTicketCategory> findByActiveTrueOrderBySortOrderAsc();

}
