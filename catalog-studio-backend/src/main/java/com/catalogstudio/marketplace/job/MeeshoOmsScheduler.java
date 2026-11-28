package com.catalogstudio.marketplace.job;

import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.repository.MarketplaceConnectionRepository;
import com.catalogstudio.marketplace.service.MeeshoPanelSyncService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Base-style Meesho order poll. Each connected seller is synced with their own credentials
 * and their own inventory/orders — never a shared hardcoded Meesho login.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeeshoOmsScheduler {

    private final CatalogStudioProperties properties;
    private final MarketplaceConnectionRepository connectionRepository;
    private final MeeshoPanelSyncService syncService;

    @Scheduled(fixedDelayString = "${catalogstudio.oms.meesho.order-poll-ms:300000}", initialDelayString = "120000")
    public void pollMeeshoOrders() {
        CatalogStudioProperties.Oms.Meesho cfg = properties.oms().meesho();
        if (!cfg.enabled() || !cfg.autoSyncEnabled()) {
            return;
        }
        Set<Long> userIds = new HashSet<>();
        List<MarketplaceConnection> connections = connectionRepository.findConnectedMeeshoPanel();
        for (MarketplaceConnection connection : connections) {
            if (connection.getUser() != null) {
                userIds.add(connection.getUser().getId());
            }
        }
        // Multi-user: only sellers who connected their own Meesho. No global password fallback.
        for (Long userId : userIds) {
            try {
                syncService.syncOrders(userId);
                syncService.syncReturns(userId);
            } catch (Exception ex) {
                log.warn("Scheduled Meesho sync failed for user {}: {}", userId, ex.toString());
            }
        }
    }
}
