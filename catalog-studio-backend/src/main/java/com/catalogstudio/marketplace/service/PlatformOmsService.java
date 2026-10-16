package com.catalogstudio.marketplace.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.inventory.entity.InventoryLevel;
import com.catalogstudio.inventory.repository.InventoryLevelRepository;
import com.catalogstudio.marketplace.client.AmazonSpApiClient;
import com.catalogstudio.marketplace.client.FlipkartSellerApiClient;
import com.catalogstudio.marketplace.dto.MeeshoChannelOrderResponse;
import com.catalogstudio.marketplace.dto.MeeshoSyncResultResponse;
import com.catalogstudio.marketplace.dto.OmsChannelStatusResponse;
import com.catalogstudio.marketplace.dto.OmsPlatformStatusResponse;
import com.catalogstudio.marketplace.entity.MarketplaceChannelOrder;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.entity.MarketplaceConnection.ConnectionStatus;
import com.catalogstudio.marketplace.entity.MarketplaceSyncRun;
import com.catalogstudio.marketplace.meesho.MeeshoLabelResult;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient.AuthResult;
import com.catalogstudio.marketplace.meesho.MeeshoPanelSession;
import com.catalogstudio.marketplace.meesho.MeeshoRemoteOrder;
import com.catalogstudio.marketplace.meesho.MeeshoStockUpdate;
import com.catalogstudio.marketplace.oms.OmsChannel;
import com.catalogstudio.marketplace.repository.MarketplaceChannelOrderRepository;
import com.catalogstudio.marketplace.repository.MarketplaceConnectionRepository;
import com.catalogstudio.marketplace.repository.MarketplaceSyncRunRepository;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.user.repository.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PlatformOmsService {

    private final CatalogStudioProperties properties;
    private final MarketplaceConnectionRepository connectionRepository;
    private final MarketplaceChannelOrderRepository orderRepository;
    private final MarketplaceSyncRunRepository syncRunRepository;
    private final InventoryLevelRepository inventoryLevelRepository;
    private final UserRepository userRepository;
    private final MeeshoPanelClient meeshoPanelClient;
    private final FlipkartSellerApiClient flipkartSellerApiClient;
    private final AmazonSpApiClient amazonSpApiClient;
    private final OmsAutoActionService autoActionService;

    @Transactional(readOnly = true)
    public OmsPlatformStatusResponse status() {
        CatalogStudioProperties.Oms oms = properties.oms();
        return new OmsPlatformStatusResponse(
                "PROPERTY_FILE",
                List.of(flipkartStatus(oms.flipkart()), amazonStatus(oms.amazon()), meeshoStatus(oms.meesho())),
                "Credentials from .env / .env.gcp. Super admin runs Sync. Live Flipkart/Amazon/Meesho clients are active.");
    }

    @Transactional(readOnly = true)
    public List<MeeshoChannelOrderResponse> listOrders(String channel) {
        if (channel == null || "ALL".equalsIgnoreCase(channel.trim())) {
            return orderRepository.findByUserIdOrderByOrderedAtDesc(platformUserId()).stream()
                    .map(this::toOrder)
                    .toList();
        }
        OmsChannel ch = parseChannel(channel);
        return orderRepository
                .findByUserIdAndMarketplaceOrderByOrderedAtDesc(platformUserId(), ch.name())
                .stream()
                .map(this::toOrder)
                .toList();
    }

    @Transactional
    public MeeshoSyncResultResponse sync(String channel, String syncType) {
        OmsChannel ch = parseChannel(channel);
        String type = syncType == null ? "FULL" : syncType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ORDERS", "STOCK", "LABELS", "FULL").contains(type)) {
            throw ApiException.badRequest("syncType must be ORDERS, STOCK, LABELS, or FULL");
        }
        ensureConfigured(ch);
        MarketplaceConnection connection = ensurePlatformConnection(ch);
        if ("FULL".equals(type)) {
            MeeshoSyncResultResponse o = runOne(ch, connection, "ORDERS");
            MeeshoSyncResultResponse s = runOne(ch, connection, "STOCK");
            MeeshoSyncResultResponse l = runOne(ch, connection, "LABELS");
            int ok = o.itemsOk() + s.itemsOk() + l.itemsOk();
            int failed = o.itemsFailed() + s.itemsFailed() + l.itemsFailed();
            String status = failed == 0 ? "SUCCESS" : (ok > 0 ? "PARTIAL" : "FAILED");
            MarketplaceSyncRun run = syncRunRepository.save(MarketplaceSyncRun.builder()
                    .connection(connection)
                    .user(userRepository.getReferenceById(platformUserId()))
                    .marketplace(ch.name())
                    .syncType("FULL")
                    .status(status)
                    .itemsOk(ok)
                    .itemsFailed(failed)
                    .message("Orders: " + o.message() + " | Stock: " + s.message() + " | Labels: " + l.message())
                    .startedAt(Instant.now())
                    .finishedAt(Instant.now())
                    .build());
            return toRun(run);
        }
        return runOne(ch, connection, type);
    }

    private MeeshoSyncResultResponse runOne(OmsChannel channel, MarketplaceConnection connection, String type) {
        MarketplaceSyncRun run = syncRunRepository.save(MarketplaceSyncRun.builder()
                .connection(connection)
                .user(userRepository.getReferenceById(platformUserId()))
                .marketplace(channel.name())
                .syncType(type)
                .status("RUNNING")
                .startedAt(Instant.now())
                .build());
        try {
            SyncCounts counts = switch (type) {
                case "ORDERS" -> syncOrders(channel, connection);
                case "STOCK" -> syncStock(channel, connection);
                case "LABELS" -> syncLabels(channel, connection);
                default -> throw ApiException.badRequest("Unsupported sync type");
            };
            run.setItemsOk(counts.ok());
            run.setItemsFailed(counts.failed());
            run.setMessage(counts.message());
            run.setStatus(counts.failed() == 0 ? "SUCCESS" : (counts.ok() > 0 ? "PARTIAL" : "FAILED"));
            run.setFinishedAt(Instant.now());
            syncRunRepository.save(run);
            return toRun(run);
        } catch (RuntimeException ex) {
            run.setStatus("FAILED");
            run.setMessage(ex.getMessage());
            run.setFinishedAt(Instant.now());
            syncRunRepository.save(run);
            throw ApiException.badRequest(ex.getMessage() == null ? "OMS sync failed" : ex.getMessage());
        }
    }

    private SyncCounts syncOrders(OmsChannel channel, MarketplaceConnection connection) {
        List<MeeshoRemoteOrder> remote = switch (channel) {
            case FLIPKART -> flipkartSellerApiClient.fetchOrders();
            case AMAZON -> amazonSpApiClient.fetchOrders();
            case MEESHO -> {
                CatalogStudioProperties.Oms.Meesho cfg = properties.oms().meesho();
                AuthResult auth = meeshoPanelClient.authenticate(cfg.email().trim(), cfg.password());
                if (!auth.success()) {
                    throw new IllegalStateException(auth.message());
                }
                yield meeshoPanelClient.fetchPendingOrders(auth.session());
            }
        };
        int ok = 0;
        for (MeeshoRemoteOrder item : remote) {
            MarketplaceChannelOrder saved = upsertOrder(connection, channel.name(), item);
            autoActionService.onOrderImported(platformUserId(), channel.name(), saved);
            ok++;
        }
        touch(connection, "lastOrdersSyncAt");
        return new SyncCounts(ok, 0, channel.name() + ": pulled " + ok + " order(s).");
    }

    private SyncCounts syncStock(OmsChannel channel, MarketplaceConnection connection) {
        List<InventoryLevel> levels = inventoryLevelRepository.findAllForUser(platformUserId());
        List<MeeshoStockUpdate> updates = levels.stream()
                .map(level -> new MeeshoStockUpdate(
                        level.getVariant().getSku(),
                        Math.max(0, level.getOnHand() - level.getReserved())))
                .filter(u -> StringUtils.hasText(u.sku()))
                .toList();
        int pushed = switch (channel) {
            case FLIPKART -> flipkartSellerApiClient.pushStock(updates);
            case AMAZON -> amazonSpApiClient.pushStock(updates);
            case MEESHO -> {
                CatalogStudioProperties.Oms.Meesho cfg = properties.oms().meesho();
                AuthResult auth = meeshoPanelClient.authenticate(cfg.email().trim(), cfg.password());
                if (!auth.success()) {
                    throw new IllegalStateException(auth.message());
                }
                yield meeshoPanelClient.pushStock(auth.session(), updates);
            }
        };
        touch(connection, "lastStockSyncAt");
        int failed = Math.max(0, updates.size() - pushed);
        return new SyncCounts(pushed, failed, channel.name()
                + ": pushed " + pushed + "/" + updates.size() + " SKU(s) from Catalog Studio inventory.");
    }

    private SyncCounts syncLabels(OmsChannel channel, MarketplaceConnection connection) {
        List<MarketplaceChannelOrder> candidates = orderRepository.findByUserIdAndMarketplaceAndLabelStatusIn(
                platformUserId(), channel.name(), List.of("NONE", "PENDING", "ERROR"));
        if (candidates.isEmpty()) {
            touch(connection, "lastLabelsSyncAt");
            return new SyncCounts(0, 0, channel.name() + ": no orders waiting for labels.");
        }
        List<String> ids = candidates.stream()
                .map(o -> StringUtils.hasText(o.getExternalOrderId()) ? o.getExternalOrderId() : null)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<MeeshoLabelResult> labels = switch (channel) {
            case FLIPKART -> flipkartSellerApiClient.fetchLabels(ids);
            case AMAZON -> amazonSpApiClient.fetchLabels(ids);
            case MEESHO -> {
                CatalogStudioProperties.Oms.Meesho cfg = properties.oms().meesho();
                AuthResult auth = meeshoPanelClient.authenticate(cfg.email().trim(), cfg.password());
                MeeshoPanelSession session = auth.success() ? auth.session() : null;
                yield meeshoPanelClient.fetchLabels(session, ids);
            }
        };
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
        touch(connection, "lastLabelsSyncAt");
        return new SyncCounts(ok, failed, channel.name() + ": labels ready=" + ok + " pending/error=" + failed);
    }

    private MarketplaceChannelOrder upsertOrder(MarketplaceConnection connection, String marketplace, MeeshoRemoteOrder item) {
        String sub = item.externalSubOrderId() == null ? "" : item.externalSubOrderId();
        MarketplaceChannelOrder order = orderRepository
                .findByConnectionIdAndExternalOrderIdAndExternalSubOrderId(
                        connection.getId(), item.externalOrderId(), sub)
                .orElseGet(() -> MarketplaceChannelOrder.builder()
                        .connection(connection)
                        .user(userRepository.getReferenceById(platformUserId()))
                        .marketplace(marketplace)
                        .externalOrderId(item.externalOrderId())
                        .externalSubOrderId(sub)
                        .build());
        order.setStatus(StringUtils.hasText(item.status()) ? item.status() : "PENDING");
        order.setChannelStatus(order.getStatus());
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
        return orderRepository.save(order);
    }

    private MarketplaceConnection ensurePlatformConnection(OmsChannel channel) {
        Long userId = platformUserId();
        MarketplaceConnection connection = connectionRepository.findByUserIdAndMarketplace(userId, channel.name())
                .orElseGet(() -> MarketplaceConnection.builder()
                        .user(userRepository.getReferenceById(userId))
                        .marketplace(channel.name())
                        .build());
        connection.setConnectionType(switch (channel) {
            case FLIPKART -> "SELLER_API";
            case AMAZON -> "SP_API";
            case MEESHO -> "PANEL_LOGIN";
        });
        connection.setStatus(ConnectionStatus.CONNECTED);
        Map<String, Object> cfg = new HashMap<>(
                connection.getConfigurationJson() == null ? Map.of() : connection.getConfigurationJson());
        cfg.put("credentialSource", "PROPERTY_FILE");
        cfg.put("syncEnabled", true);
        cfg.put("syncNote", "Live OMS client using property/env credentials.");
        connection.setConfigurationJson(cfg);
        connection.setLastActivityAt(Instant.now());
        return connectionRepository.save(connection);
    }

    private void touch(MarketplaceConnection connection, String key) {
        Map<String, Object> cfg = new HashMap<>(
                connection.getConfigurationJson() == null ? Map.of() : connection.getConfigurationJson());
        cfg.put(key, Instant.now().toString());
        connection.setConfigurationJson(cfg);
        connection.setLastActivityAt(Instant.now());
        connectionRepository.save(connection);
    }

    private void ensureConfigured(OmsChannel channel) {
        CatalogStudioProperties.Oms oms = properties.oms();
        boolean ok = switch (channel) {
            case FLIPKART -> oms.flipkart().enabled() && oms.flipkart().configured();
            case AMAZON -> oms.amazon().enabled() && oms.amazon().configured();
            case MEESHO -> oms.meesho().enabled() && oms.meesho().configured();
        };
        if (!ok) {
            throw ApiException.badRequest(channel.name()
                    + " OMS is not configured in property file (enabled + credentials).");
        }
    }

    private OmsChannelStatusResponse flipkartStatus(CatalogStudioProperties.Oms.Flipkart cfg) {
        return new OmsChannelStatusResponse(
                "FLIPKART",
                cfg.enabled(),
                cfg.configured(),
                false,
                maskId(cfg.appId()),
                cfg.configured()
                        ? "Live Flipkart Seller API ready (token + orders + inventory)"
                        : "Set FLIPKART_OMS_ENABLED=true and FLIPKART_APP_ID / SECRET / LOCATION_ID");
    }

    private OmsChannelStatusResponse amazonStatus(CatalogStudioProperties.Oms.Amazon cfg) {
        return new OmsChannelStatusResponse(
                "AMAZON",
                cfg.enabled(),
                cfg.configured(),
                cfg.sandbox(),
                maskId(cfg.clientId()),
                cfg.configured()
                        ? "Live Amazon SP-API ready (LWA + orders + inventory)"
                        : "Set AMAZON_OMS_ENABLED=true and AMAZON_LWA_* credentials");
    }

    private OmsChannelStatusResponse meeshoStatus(CatalogStudioProperties.Oms.Meesho cfg) {
        return new OmsChannelStatusResponse(
                "MEESHO",
                cfg.enabled(),
                cfg.configured(),
                false,
                maskEmail(cfg.email()),
                cfg.configured()
                        ? "Meesho panel client ready (orders/stock/labels best-effort live routes)"
                        : "Set MEESHO_OMS_ENABLED=true and MEESHO_PANEL_EMAIL / PASSWORD");
    }

    private Long platformUserId() {
        return SecurityUtils.currentUserId();
    }

    private static OmsChannel parseChannel(String raw) {
        try {
            return OmsChannel.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw ApiException.badRequest("channel must be FLIPKART, AMAZON, or MEESHO");
        }
    }

    private MeeshoChannelOrderResponse toOrder(MarketplaceChannelOrder order) {
        return new MeeshoChannelOrderResponse(
                order.getUuid(),
                order.getMarketplace(),
                order.getExternalOrderId(),
                StringUtils.hasText(order.getExternalSubOrderId()) ? order.getExternalSubOrderId() : null,
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

    private MeeshoSyncResultResponse toRun(MarketplaceSyncRun run) {
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

    private static String maskId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private static String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private record SyncCounts(int ok, int failed, String message) {}
}
