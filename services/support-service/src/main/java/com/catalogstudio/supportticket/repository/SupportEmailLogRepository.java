package com.catalogstudio.supportticket.repository;

import com.catalogstudio.supportticket.entity.SupportEmailLog;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportEmailLogRepository extends JpaRepository<SupportEmailLog, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

}
