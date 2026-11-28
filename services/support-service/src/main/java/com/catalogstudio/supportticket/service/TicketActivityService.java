package com.catalogstudio.supportticket.service;

import com.catalogstudio.supportticket.dto.TicketActivityResponse;
import com.catalogstudio.supportticket.entity.SupportTicketActivityLog;
import com.catalogstudio.supportticket.repository.SupportTicketActivityLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketActivityService {

    private final SupportTicketActivityLogRepository activityLogRepository;

    public void log(Long ticketId, Long actorUserId, String actorName,
                    String action, String fromValue, String toValue, String metadata) {
        activityLogRepository.save(SupportTicketActivityLog.builder()
                .ticketId(ticketId)
                .actorUserId(actorUserId)
                .actorName(actorName)
                .action(action)
                .fromValue(fromValue)
                .toValue(toValue)
                .metadata(metadata)
                .build());
    }

    public List<TicketActivityResponse> list(Long ticketId) {
        return activityLogRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(SupportMapper::toActivity)
                .toList();
    }
}
