package com.catalogstudio.extension.repository;

import com.catalogstudio.extension.entity.ExtensionActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtensionActivityLogRepository extends JpaRepository<ExtensionActivityLog, Long> {
    Page<ExtensionActivityLog> findByUserId(Long userId, Pageable pageable);
}
