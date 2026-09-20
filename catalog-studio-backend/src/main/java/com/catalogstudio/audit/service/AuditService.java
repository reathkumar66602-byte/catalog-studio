package com.catalogstudio.audit.service;

import com.catalogstudio.audit.entity.AuditLog;
import com.catalogstudio.audit.repository.AuditLogRepository;
import com.catalogstudio.user.entity.User;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(User user, String action, String entityType, String entityId, String ip, Map<String, Object> metadata) {
        auditLogRepository.save(AuditLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress(ip)
                .metadata(metadata)
                .build());
    }
}
