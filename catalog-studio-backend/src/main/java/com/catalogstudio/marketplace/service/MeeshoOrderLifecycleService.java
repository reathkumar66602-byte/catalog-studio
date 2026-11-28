package com.catalogstudio.marketplace.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.inventory.entity.InventoryLevel;
import com.catalogstudio.inventory.repository.InventoryLevelRepository;
import com.catalogstudio.inventory.repository.ProductVariantRepository;
import com.catalogstudio.marketplace.entity.MarketplaceChannelOrder;
import com.catalogstudio.marketplace.meesho.MeeshoLabelResult;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient;
import com.catalogstudio.marketplace.meesho.MeeshoPanelSession;
import com.catalogstudio.marketplace.oms.MeeshoStatusMapper;
import com.catalogstudio.marketplace.repository.MarketplaceChannelOrderRepository;
import com.catalogstudio.security.SecurityUtils;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Accept / pack / ship / cancel Meesho orders from the unified Order Manager. */
@Service
@RequiredArgsConstructor
public class MeeshoOrderLifecycleService {

    private final MarketplaceChannelOrderRepository orderRepository;
    private final MeeshoCredentialService credentialService;
    private final MeeshoPanelClient meeshoPanelClient;
    private final MeeshoStatusMapper statusMapper;
    private final ChannelStockReservationService reservationService;
    private final MeeshoStockPushService stockPushService;
    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;

    @Transactional
    public Map<String, Object> updateStatus(UUID orderId, String internalStatus) {
        Long userId = SecurityUtils.currentUserId();
        MarketplaceChannelOrder order = orderRepository.findByUuidAndUserId(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!"MEESHO".equalsIgnoreCase(order.getMarketplace())) {
            throw ApiException.badRequest("Status push to Meesho only applies to MEESHO orders");
        }
        String oms = internalStatus.trim().toUpperCase();
        String previous = order.getOmsStatus();
        order.setOmsStatus(oms);
        order.setStatus(oms);
        order.setChannelStatus(statusMapper.toMeesho(oms));
        order.setLastSyncedAt(Instant.now());

        MeeshoPanelSession session = credentialService.requireSession(userId);
        boolean channelOk;
        if ("PROCESSING".equals(oms) || "ACCEPTED".equals(oms)) {
            channelOk = meeshoPanelClient.acceptOrder(session, order.getExternalOrderId())
                    || meeshoPanelClient.updateOrderStatus(session, order.getExternalOrderId(), statusMapper.toMeesho(oms));
        } else {
            channelOk = meeshoPanelClient.updateOrderStatus(
                    session, order.getExternalOrderId(), statusMapper.toMeesho(oms));
        }

        if (statusMapper.isTerminalCancel(oms)) {
            reservationService.releaseForOrder(userId, order);
            pushSkuSafe(userId, order);
        } else if (statusMapper.isShipped(oms)) {
            reservationService.commitSaleForOrder(userId, order);
            pushSkuSafe(userId, order);
        } else if (statusMapper.isReturn(oms)) {
            reservationService.restockReturn(userId, order);
            pushSkuSafe(userId, order);
        }

        orderRepository.save(order);
        Map<String, Object> result = new HashMap<>();
        result.put("id", order.getUuid());
        result.put("previousOmsStatus", previous);
        result.put("omsStatus", order.getOmsStatus());
        result.put("channelStatus", order.getChannelStatus());
        result.put("channelUpdated", channelOk);
        return result;
    }

    @Transactional
    public Map<String, Object> createMeeshoShipment(UUID orderId) {
        Long userId = SecurityUtils.currentUserId();
        MarketplaceChannelOrder order = orderRepository.findByUuidAndUserId(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!"MEESHO".equalsIgnoreCase(order.getMarketplace())) {
            throw ApiException.badRequest("Meesho shipment only for MEESHO orders");
        }
        MeeshoPanelSession session = credentialService.requireSession(userId);
        if (!"PACKED".equalsIgnoreCase(order.getOmsStatus()) && !"PROCESSING".equalsIgnoreCase(order.getOmsStatus())) {
            order.setOmsStatus("PACKED");
            order.setStatus("PACKED");
            order.setChannelStatus(statusMapper.toMeesho("PACKED"));
            meeshoPanelClient.updateOrderStatus(session, order.getExternalOrderId(), statusMapper.toMeesho("PACKED"));
        }
        MeeshoLabelResult label = meeshoPanelClient.createShipment(session, order.getExternalOrderId());
        if (StringUtils.hasText(label.awb())) {
            order.setAwb(label.awb());
        }
        if (StringUtils.hasText(label.labelUrl())) {
            order.setLabelUrl(label.labelUrl());
            order.setLabelStatus("READY");
        } else {
            order.setLabelStatus("PENDING".equalsIgnoreCase(label.status()) ? "PENDING" : "ERROR");
        }
        order.setCourierCode("MEESHO");
        order.setLastSyncedAt(Instant.now());
        orderRepository.save(order);
        Map<String, Object> result = new HashMap<>();
        result.put("id", order.getUuid());
        result.put("awb", order.getAwb());
        result.put("labelUrl", order.getLabelUrl());
        result.put("labelStatus", order.getLabelStatus());
        result.put("message", label.message());
        return result;
    }

    private void pushSkuSafe(Long userId, MarketplaceChannelOrder order) {
        if (!StringUtils.hasText(order.getSku())) {
            return;
        }
        try {
            stockPushService.pushSku(userId, order.getSku(), availableForSku(userId, order.getSku()), null);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private int availableForSku(Long userId, String sku) {
        return variantRepository.findAllByUserIdAndSkuIgnoreCase(userId, sku.trim()).stream()
                .findFirst()
                .flatMap(v -> levelRepository.findByVariantId(v.getId()))
                .map(InventoryLevel::available)
                .orElse(0);
    }
}
