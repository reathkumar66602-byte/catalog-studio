package com.catalogstudio.extension.repository;

import com.catalogstudio.extension.entity.ExtensionTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExtensionTicketRepository extends JpaRepository<ExtensionTicket, Long> {
    Optional<ExtensionTicket> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);
}
