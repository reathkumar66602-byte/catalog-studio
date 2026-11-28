package com.catalogstudio.supportticket.repository;

import com.catalogstudio.supportticket.entity.SupportChatMessage;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportChatMessageRepository extends JpaRepository<SupportChatMessage, Long> {

    List<SupportChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

}
