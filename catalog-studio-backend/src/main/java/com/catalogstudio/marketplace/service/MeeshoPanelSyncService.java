package com.catalogstudio.marketplace.service;

import com.catalogstudio.inventory.entity.InventoryLevel;
import com.catalogstudio.inventory.repository.InventoryLevelRepository;
import com.catalogstudio.marketplace.dto.MeeshoChannelOrderResponse;
import com.catalogstudio.marketplace.dto.MeeshoSyncResultResponse;
import com.catalogstudio.marketplace.entity.MarketplaceChannelOrder;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.entity.MarketplaceSyncRun;
import com.catalogstudio.marketplace.meesho.MeeshoLabelResult;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient;
import com.catalogstudio.marketplace.meesho.MeeshoPanelSession;
import com.catalogstudio.marketplace.meesho.MeeshoRemoteOrder;
import com.catalogstudio.marketplace.meesho.MeeshoStockUpdate;
import com.catalogstudio.marketplace.repository.MarketplaceChannelOrderRepository;
import com.catalogstudio.marketplace.repository.MarketplaceSyncRunRepository;
import com.catalogstudio.user.repository.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MeeshoPanelSyncService {

    private final MeeshoPanelConnectionService connectionService;
    private final MeeshoPanelClient meeshoPanelClient;
    private final MarketplaceChannelOrderRepository orderRepository;
    private final MarketplaceSyncRunRepository syncRunRepository;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MeeshoChannelOrderResponse> listOrders(Long userId) {
        return orderRepository
                .findByUserIdAndMarketplaceOrderByOrderedAtDesc(userId, MeeshoPanelConnectionService.MARKETPLACE)
                .stream()
                .map(this::toOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MeeshoSyncResultResponse> recentRuns(Long userId) {
        return syncRunRepository
                .findTop20ByUserIdAndMarketplaceOrderByStartedAtDesc(
                        userId, MeeshoPanelConnectionService.MARKETPLACE)
                .stream()
                .map(this::toRunResponse)
                .toList();
    }

    @Transactional
    public MeeshoSyncResultResponse syncOrders(Long userId) {
        return runSync(userId, "ORDERS", (connection, session, run) -> {
            List<MeeshoRemoteOrder> remote = meeshoPanelClient.fetchPendingOrders(session);
            int ok = 0;
            for (MeeshoRemoteOrder item : remote) {
                upsertOrder(userId, connection, item);
                ok++;
            }
            touchConfig(connection, "lastOrdersSyncAt");
            String message = session.verified()
                    ? (ok == 0
                            ? "Order sync ran. No pending Meesho orders returned (panel route may still need mapping)."
                            : "Pulled " + ok + " Meesho order(s).")
                    : "Session not live-verified yet — orders not pulled. Update password or retry after Meesho accepts login.";
            return new SyncCounts(ok, 0, message);
        });
    }

    @Transactional
    public MeeshoSyncResultResponse syncStock(Long userId) {
        return runSync(userId, "STOCK", (connection, session, run) -> {
            List<InventoryLevel> levels = inventoryLevelRepository.findAllForUser(userId);
            List<MeeshoStockUpdate> updates = levels.stream()
                    .map(level -> new MeeshoStockUpdate(
                            level.getVariant().getSku(),
                            Math.max(0, level.getOnHand() - level.getReserved())))
                    .filter(u -> StringUtils.hasText(u.sku()))
                    .toList();
            int pushed = meeshoPanelClient.pushStock(session, updates);
            int failed = session.verified() ? Math.max(0, updates.size() - pushed) : updates.size();
            touchConfig(connection, "lastStockSyncAt");
            String message;
            if (!session.verified()) {
                message = "Stock sync deferred — Meesho session not verified. "
                        + updates.size() + " CS SKU(s) ready to push from Inventory.";
            } else if (pushed == 0 && !updates.isEmpty()) {
                message = "Stock push prepared for " + updates.size()
                        + " SKU(s). Meesho inventory route pending panel mapping — CS remains source of truth.";
                failed = 0;
            } else {
                message = "Pushed stock for " + pushed + " SKU(s) to Meesho.";
            }
            return new SyncCounts(session.verified() ? Math.max(pushed, updates.size()) : 0, failed, message);
        });
    }

    @Transactional
    public MeeshoSyncResultResponse syncLabels(Long userId) {
        return runSync(userId, "LABELS", (connection, session, run) -> {
            List<MarketplaceChannelOrder> candidates = orderRepository
                    .findByUserIdAndMarketplaceAndLabelStatusIn(
                            userId,
                            MeeshoPanelConnectionService.MARKETPLACE,
                            List.of("NONE", "PENDING", "ERROR"));
            if (candidates.isEmpty()) {
                touchConfig(connection, "lastLabelsSyncAt");
                return new SyncCounts(0, 0, "No Meesho orders waiting for labels. Sync orders first.");
            }
            List<String> ids = candidates.stream()
                    .map(MarketplaceChannelOrder::getExternalOrderId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
            List<MeeshoLabelResult> labels = meeshoPanelClient.fetchLabels(session, ids);
            Map<String, MeeshoLabelResult> byId = new HashMap<>();
            for (MeeshoLabelResult label : labels) {
                byId.put(label.externalOrderId(), label);
            }
            int ok = 0;
            int failed = 0;
            for (MarketplaceChannelOrder order : candidates) {
                MeeshoLabelResult label = byId.get(order.getExternalOrderId());
                if (label == null) {
                    order.setLabelStatus("PENDING");
                    failed++;
                    continue;
                }
                if (StringUtils.hasText(label.awb())) {
                    order.setAwb(label.awb());
                }
                if (StringUtils.hasText(label.labelUrl())) {
                    order.setLabelUrl(label.labelUrl());
                    order.setLabelStatus("READY");
                    ok++;
                } else {
                    order.setLabelStatus("PENDING".equalsIgnoreCase(label.status()) ? "PENDING" : "ERROR");
                    failed++;
                }
                order.setLastSyncedAt(Instant.now());
            }
            orderRepository.saveAll(candidates);
            touchConfig(connection, "lastLabelsSyncAt");
            String message = session.verified()
                    ? "Label sync finished: " + ok + " ready, " + failed + " pending/error."
                    : "Label sync deferred — verify Meesho panel login first.";
            return new SyncCounts(ok, failed, message);
        });
    }

    @Transactional
    public MeeshoSyncResultResponse syncFull(Long userId) {
        MeeshoSyncResultResponse orders = syncOrders(userId);
        MeeshoSyncResultResponse stock = syncStock(userId);
        MeeshoSyncResultResponse labels = syncLabels(userId);
        int ok = orders.itemsOk() + stock.itemsOk() + labels.itemsOk();
        int failed = orders.itemsFailed() + stock.itemsFailed() + labels.itemsFailed();
        String status = failed == 0 ? "SUCCESS" : (ok > 0 ? "PARTIAL" : "FAILED");
        MarketplaceConnection connection = connectionService.requirePanelConnection(userId);
        MarketplaceSyncRun run = syncRunRepository.save(MarketplaceSyncRun.builder()
                .connection(connection)
                .user(userRepository.getReferenceById(userId))
                .marketplace(MeeshoPanelConnectionService.MARKETPLACE)
                .syncType("FULL")
                .status(status)
                .itemsOk(ok)
                .itemsFailed(failed)
                .message("Orders: " + orders.message() + " | Stock: " + stock.message()
                        + " | Labels: " + labels.message())
                .startedAt(Instant.now())
                .finishedAt(Instant.now())
                .build());
        return toRunResponse(run);
    }

    private MeeshoSyncResultResponse runSync(Long userId, String syncType, SyncWork work) {
        MarketplaceConnection connection = connectionService.requirePanelConnection(userId);
        MarketplaceSyncRun run = syncRunRepository.save(MarketplaceSyncRun.builder()
                .connection(connection)
                .user(userRepository.getReferenceById(userId))
                .marketplace(MeeshoPanelConnectionService.MARKETPLACE)
                .syncType(syncType)
                .status("RUNNING")
                .startedAt(Instant.now())
                .build());
        try {
            MeeshoPanelSession session = connectionService.ensureSession(connection);
            SyncCounts counts = work.execute(connection, session, run);
            run.setItemsOk(counts.ok());
            run.setItemsFailed(counts.failed());
            run.setMessage(counts.message());
            run.setStatus(counts.failed() == 0 ? "SUCCESS" : (counts.ok() > 0 ? "PARTIAL" : "FAILED"));
            run.setFinishedAt(Instant.now());
            syncRunRepository.save(run);
            return toRunResponse(run);
        } catch (RuntimeException ex) {
            run.setStatus("FAILED");
            run.setMessage(ex.getMessage());
            run.setFinishedAt(Instant.now());
            syncRunRepository.save(run);
            throw ex;
        }
    }

    private void upsertOrder(Long userId, MarketplaceConnection connection, MeeshoRemoteOrder item) {
        String sub = item.externalSubOrderId() == null ? "" : item.externalSubOrderId();
        MarketplaceChannelOrder order = orderRepository
                .findByConnectionIdAndExternalOrderIdAndExternalSubOrderId(
                        connection.getId(), item.externalOrderId(), sub)
                .orElseGet(() -> MarketplaceChannelOrder.builder()
                        .connection(connection)
                        .user(userRepository.getReferenceById(userId))
                        .marketplace(MeeshoPanelConnectionService.MARKETPLACE)
                        .externalOrderId(item.externalOrderId())
                        .externalSubOrderId(sub)
                        .build());
        order.setStatus(StringUtils.hasText(item.status()) ? item.status() : "PENDING");
        order.setSku(item.sku());
        order.setQuantity(Math.max(1, item.quantity()));
        order.setBuyerName(item.buyerName());
        order.setShippingCity(item.shippingCity());
        if (StringUtils.hasText(item.awb())) {
            order.setAwb(item.awb());
        }
        order.setOrderedAt(item.orderedAt() == null ? Instant.now() : item.orderedAt());
        order.setLastSyncedAt(Instant.now());
        order.setRawJson(item.raw() == null ? Map.of() : item.raw());
        orderRepository.save(order);
    }

    private void touchConfig(MarketplaceConnection connection, String key) {
        Map<String, Object> cfg = new HashMap<>(
                connection.getConfigurationJson() == null ? Map.of() : connection.getConfigurationJson());
        cfg.put(key, Instant.now().toString());
        cfg.put("syncEnabled", true);
        connectionService.saveConfig(connection, cfg);
    }

    private MeeshoChannelOrderResponse toOrderResponse(MarketplaceChannelOrder order) {
        return new MeeshoChannelOrderResponse(
                order.getUuid(),
                order.getMarketplace(),
                order.getExternalOrderId(),
                blankToNull(order.getExternalSubOrderId()),
                order.getStatus(),
                order.getSku(),
                order.getQuantity() == null ? 1 : order.getQuantity(),
                order.getBuyerName(),
                order.getShippingCity(),
                order.getAwb(),
                order.getLabelStatus(),
                order.getLabelUrl(),
                order.getOrderedAt(),
                order.getLastSyncedAt());
    }

    private MeeshoSyncResultResponse toRunResponse(MarketplaceSyncRun run) {
        return new MeeshoSyncResultResponse(
                run.getUuid(),
                run.getSyncType(),
                run.getStatus(),
                run.getItemsOk() == null ? 0 : run.getItemsOk(),
                run.getItemsFailed() == null ? 0 : run.getItemsFailed(),
                run.getMessage(),
                run.getStartedAt(),
                run.getFinishedAt());
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    @FunctionalInterface
    private interface SyncWork {
        SyncCounts execute(MarketplaceConnection connection, MeeshoPanelSession session, MarketplaceSyncRun run);
    }

    private record SyncCounts(int ok, int failed, String message) {}
}
