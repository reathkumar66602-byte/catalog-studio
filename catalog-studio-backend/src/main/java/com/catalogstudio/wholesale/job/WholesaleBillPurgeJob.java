package com.catalogstudio.wholesale.job;

import com.catalogstudio.storage.StorageService;
import com.catalogstudio.wholesale.entity.WholesaleOrder;
import com.catalogstudio.wholesale.repository.WholesaleOrderItemRepository;
import com.catalogstudio.wholesale.repository.WholesaleOrderRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Keeps wholesale bill history for one year, then permanently removes older orders and PDF files.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalogstudio.modules.wholesale-enabled", havingValue = "true", matchIfMissing = true)
public class WholesaleBillPurgeJob {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final WholesaleOrderRepository orderRepository;
    private final WholesaleOrderItemRepository orderItemRepository;
    private final StorageService storageService;

    @Value("${catalogstudio.wholesale.bill-retention-days:365}")
    private int retentionDays;

    @Scheduled(cron = "${catalogstudio.wholesale.bill-purge-cron:0 15 4 * * *}")
    @Transactional
    public void purge() {
        Instant cutoff = ZonedDateTime.now(IST).minusDays(Math.max(retentionDays, 30)).toInstant();
        int removed = 0;
        // Batch so a large backlog does not lock the table forever.
        for (int i = 0; i < 20; i++) {
            List<WholesaleOrder> batch = orderRepository.findOlderThan(cutoff, PageRequest.of(0, 100));
            if (batch.isEmpty()) {
                break;
            }
            for (WholesaleOrder order : batch) {
                if (StringUtils.hasText(order.getBillPdfStorageKey())) {
                    try {
                        storageService.delete(order.getBillPdfStorageKey());
                    } catch (RuntimeException ex) {
                        log.warn("Could not delete bill PDF {}: {}", order.getBillPdfStorageKey(), ex.toString());
                    }
                }
                orderItemRepository.deleteByOrderId(order.getId());
                orderRepository.delete(order);
                removed++;
            }
            orderRepository.flush();
        }
        if (removed > 0) {
            log.info("Purged {} wholesale bills older than {} days", removed, retentionDays);
        }
    }
}
