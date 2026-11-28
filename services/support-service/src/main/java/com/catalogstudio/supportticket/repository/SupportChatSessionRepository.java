package com.catalogstudio.supportticket.repository;

import com.catalogstudio.supportticket.entity.SupportChatSession;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportChatSessionRepository extends JpaRepository<SupportChatSession, Long> {

    Optional<SupportChatSession> findByUuid(UUID uuid);
    Optional<SupportChatSession> findByUuidAndCustomerUserId(UUID uuid, Long customerUserId);

}
