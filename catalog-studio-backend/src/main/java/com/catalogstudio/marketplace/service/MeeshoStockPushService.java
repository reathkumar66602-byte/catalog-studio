package com.catalogstudio.marketplace.service;

import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.inventory.entity.InventoryLevel;
import com.catalogstudio.inventory.event.InventoryStockChangedEvent;
import com.catalogstudio.inventory.repository.InventoryLevelRepository;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.entity.MarketplaceListing;
import com.catalogstudio.marketplace.meesho.MeeshoPanelSession;
import com.catalogstudio.marketplace.meesho.MeeshoStockUpdate;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient;
import com.catalogstudio.marketplace.oms.OmsChannel;
import com.catalogstudio.marketplace.repository.MarketplaceListingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * Delta stock/price push to Meesho (Base.com style). Prefers linked offers; falls back to all SKUs on full sync.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeeshoStockPushService {

    private final CatalogStudioProperties properties;
    private final MeeshoCredentialService credentialService;
    private final MeeshoPanelClient meeshoPanelClient;
    private final MarketplaceListingRepository listingRepository;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final com.catalogstudio.user.repository.UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(InventoryStockChangedEvent event) {
        if (!properties.oms().meesho().enabled() || !properties.oms().meesho().pushStockOnChange()) {
            return;
        }
        if (event == null || !StringUtils.hasText(event.sku())) {
            return;
        }
        // Only push to THIS seller's Meesho account (never a shared property-file shop).
        if (!credentialService.hasUserConnection(event.userId())
                && !properties.oms().meesho().preferPropertyCredentials()) {
            return;
        }
        try {
            pushSku(event.userId(), event.sku(), event.available(), null);
        } catch (Exception ex) {
            log.warn("Meesho auto stock push failed for user {} sku {}: {}",
                    event.userId(), event.sku(), ex.toString());
        }
    }

    @Transactional
    public Map<String, Object> pushSku(Long userId, String sku, int available, BigDecimal price) {
        MarketplaceConnection connection = credentialService.ensureConnection(userId, userRepository);
        MeeshoPanelSession session = credentialService.requireSession(userId);
        List<MarketplaceListing> linked = listingRepository.findActiveForUserAndMarketplace(userId, OmsChannel.MEESHO.name())
                .stream()
                .filter(l -> sku.equalsIgnoreCase(l.getExternalSku())
                        || sku.equalsIgnoreCase(l.getVariant().getSku()))
                .toList();

        List<MeeshoStockUpdate> updates = List.of(new MeeshoStockUpdate(sku, Math.max(0, available)));
        int pushed = meeshoPanelClient.pushStock(session, updates);
        if (price != null) {
            meeshoPanelClient.pushPrices(session, Map.of(sku, price.doubleValue()));
        }

        if (linked.isEmpty()) {
            // Soft-link: create listing row so future delta sync has an offer link.
            inventoryLevelRepository.findAllForUser(userId).stream()
                    .filter(l -> sku.equalsIgnoreCase(l.getVariant().getSku()))
                    .findFirst()
                    .ifPresent(level -> {
                        MarketplaceListing listing = listingRepository
                                .findByVariantIdAndConnectionId(level.getVariant().getId(), connection.getId())
                                .orElseGet(() -> MarketplaceListing.builder()
                                        .variant(level.getVariant())
                                        .connection(connection)
                                        .build());
                        listing.setExternalSku(sku);
                        listing.setStockMirror(available);
                        listing.setLastPushedStock(available);
                        if (price != null) {
                            listing.setPrice(price);
                            listing.setLastPushedPrice(price);
                        }
                        listing.setStatus(available <= 0 ? "PAUSED" : "LIVE");
                        listing.setLastSyncedAt(Instant.now());
                        listing.setLastError(pushed > 0 ? null : "Stock push returned 0 — verify Meesho inventory route");
                        listingRepository.save(listing);
                    });
        } else {
            for (MarketplaceListing listing : linked) {
                listing.setStockMirror(available);
                listing.setLastPushedStock(available);
                if (price != null) {
                    listing.setPrice(price);
                    listing.setLastPushedPrice(price);
                }
                if (available <= 0) {
                    listing.setStatus("PAUSED");
                } else if ("PAUSED".equalsIgnoreCase(listing.getStatus()) || "DRAFT".equalsIgnoreCase(listing.getStatus())) {
                    listing.setStatus("LIVE");
                }
                listing.setLastSyncedAt(Instant.now());
                listing.setLastError(pushed > 0 ? null : "Stock push returned 0 — verify Meesho inventory route");
                listingRepository.save(listing);
            }
        }
        return Map.of(
                "sku", sku,
                "available", available,
                "pushed", pushed,
                "linkedOffers", linked.size());
    }

    @Transactional
    public SyncResult pushAllDelta(Long userId, MarketplaceConnection connection, MeeshoPanelSession session) {
        List<MarketplaceListing> linked = listingRepository.findLinkedForUserAndConnection(userId, connection.getId());
        List<InventoryLevel> levels = inventoryLevelRepository.findAllForUser(userId);
        Map<String, Integer> availableBySku = new HashMap<>();
        for (InventoryLevel level : levels) {
            if (level.getVariant() != null && StringUtils.hasText(level.getVariant().getSku())) {
                availableBySku.put(
                        level.getVariant().getSku().trim().toUpperCase(),
                        Math.max(0, level.getOnHand() - level.getReserved()));
            }
        }

        List<MeeshoStockUpdate> toPush = new ArrayList<>();
        List<MarketplaceListing> touched = new ArrayList<>();
        if (!linked.isEmpty()) {
            for (MarketplaceListing listing : linked) {
                String sku = StringUtils.hasText(listing.getExternalSku())
                        ? listing.getExternalSku()
                        : listing.getVariant().getSku();
                if (!StringUtils.hasText(sku)) {
                    continue;
                }
                Integer available = availableBySku.get(sku.trim().toUpperCase());
                if (available == null) {
                    available = availableBySku.getOrDefault(
                            listing.getVariant().getSku().trim().toUpperCase(), 0);
                }
                Integer last = listing.getLastPushedStock();
                if (last != null && last.equals(available)) {
                    continue;
                }
                toPush.add(new MeeshoStockUpdate(sku, available));
                listing.setStockMirror(available);
                listing.setLastPushedStock(available);
                listing.setStatus(available <= 0 ? "PAUSED" : "LIVE");
                listing.setLastSyncedAt(Instant.now());
                touched.add(listing);
            }
        } else {
            for (Map.Entry<String, Integer> e : availableBySku.entrySet()) {
                toPush.add(new MeeshoStockUpdate(e.getKey(), e.getValue()));
            }
        }

        int pushed = toPush.isEmpty() ? 0 : meeshoPanelClient.pushStock(session, toPush);
        for (MarketplaceListing listing : touched) {
            listing.setLastError(pushed > 0 || toPush.isEmpty() ? null : "Partial/failed Meesho stock push");
            listingRepository.save(listing);
        }
        return new SyncResult(toPush.size(), pushed, Math.max(0, toPush.size() - pushed),
                toPush.isEmpty()
                        ? "No stock deltas to push (linked offers already match CS inventory)."
                        : "Pushed " + pushed + "/" + toPush.size() + " Meesho stock delta(s).");
    }

    public record SyncResult(int candidates, int ok, int failed, String message) {}
}
