package com.catalogstudio.marketplace.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.marketplace.client.AmazonSpApiClient;
import com.catalogstudio.marketplace.client.FlipkartSellerApiClient;
import com.catalogstudio.marketplace.entity.MarketplaceChannelOrder;
import com.catalogstudio.marketplace.entity.OmsAutoAction;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient;
import com.catalogstudio.marketplace.repository.OmsAutoActionRepository;
import com.catalogstudio.user.repository.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Base-style auto-actions: trigger on order import / stock / price events. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OmsAutoActionService {

    private final OmsAutoActionRepository actionRepository;
    private final UserRepository userRepository;
    private final FlipkartSellerApiClient flipkartSellerApiClient;
    private final AmazonSpApiClient amazonSpApiClient;
    private final MeeshoPanelClient meeshoPanelClient;
    private final MeeshoCredentialService meeshoCredentialService;

    @Transactional(readOnly = true)
    public List<OmsAutoAction> list(Long userId) {
        ensureDefaults(userId);
        return actionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public OmsAutoAction upsert(
            Long userId,
            UUID id,
            String name,
            boolean enabled,
            String triggerEvent,
            String channel,
            Map<String, Object> condition,
            Map<String, Object> action) {
        OmsAutoAction row = id == null
                ? OmsAutoAction.builder().user(userRepository.getReferenceById(userId)).build()
                : actionRepository.findByUuidAndUserId(id, userId)
                        .orElseThrow(() -> ApiException.notFound("Auto-action not found"));
        row.setName(name);
        row.setEnabled(enabled);
        row.setTriggerEvent(triggerEvent);
        row.setChannel(channel);
        row.setConditionJson(condition == null ? Map.of() : condition);
        row.setActionJson(action == null ? Map.of() : action);
        return actionRepository.save(row);
    }

    @Transactional
    public void onOrderImported(Long userId, String channel, MarketplaceChannelOrder order) {
        for (OmsAutoAction rule : actionRepository.findByUserIdAndEnabledTrueOrderByCreatedAtAsc(userId)) {
            if (!"ORDER_IMPORTED".equalsIgnoreCase(rule.getTriggerEvent())) {
                continue;
            }
            if (StringUtils.hasText(rule.getChannel()) && !rule.getChannel().equalsIgnoreCase(channel)) {
                continue;
            }
            runAction(userId, rule, order);
        }
    }

    @Transactional
    public int syncPrices(Long userId, String channel, Map<String, Double> skuPrices) {
        int ok = 0;
        for (Map.Entry<String, Double> e : skuPrices.entrySet()) {
            boolean done = switch (channel.toUpperCase()) {
                case "FLIPKART" -> flipkartSellerApiClient.updateListingPrice(e.getKey(), e.getValue());
                case "AMAZON" -> amazonSpApiClient.updateListingPrice(e.getKey(), e.getValue());
                case "MEESHO" -> meeshoPanelClient.pushPrices(
                        meeshoCredentialService.requireSession(userId),
                        Map.of(e.getKey(), e.getValue())) > 0;
                default -> false;
            };
            if (done) {
                ok++;
            }
        }
        for (OmsAutoAction rule : actionRepository.findByUserIdAndEnabledTrueOrderByCreatedAtAsc(userId)) {
            if ("PRICE_CHANGED".equalsIgnoreCase(rule.getTriggerEvent())) {
                rule.setLastRunAt(Instant.now());
                actionRepository.save(rule);
            }
        }
        return ok;
    }

    private void runAction(Long userId, OmsAutoAction rule, MarketplaceChannelOrder order) {
        Map<String, Object> action = rule.getActionJson() == null ? Map.of() : rule.getActionJson();
        String type = String.valueOf(action.getOrDefault("type", "MARK_STATUS"));
        if ("MARK_STATUS".equalsIgnoreCase(type)) {
            String status = String.valueOf(action.getOrDefault("status", "PROCESSING"));
            order.setStatus(status);
            order.setOmsStatus(status);
            order.setChannelStatus(status);
        } else if ("CREATE_MEESHO_SHIPMENT".equalsIgnoreCase(type)
                && "MEESHO".equalsIgnoreCase(order.getMarketplace())) {
            try {
                var session = meeshoCredentialService.requireSession(userId);
                var label = meeshoPanelClient.createShipment(session, order.getExternalOrderId());
                if (StringUtils.hasText(label.awb())) {
                    order.setAwb(label.awb());
                }
                if (StringUtils.hasText(label.labelUrl())) {
                    order.setLabelUrl(label.labelUrl());
                    order.setLabelStatus("READY");
                }
                order.setCourierCode("MEESHO");
            } catch (Exception ex) {
                log.warn("Auto Meesho shipment failed for {}: {}", order.getExternalOrderId(), ex.toString());
            }
        }
        rule.setLastRunAt(Instant.now());
        actionRepository.save(rule);
        log.info("Auto-action {} ran for order {}", rule.getName(), order.getExternalOrderId());
    }

    private void ensureDefaults(Long userId) {
        if (!actionRepository.findByUserIdOrderByCreatedAtDesc(userId).isEmpty()) {
            return;
        }
        Map<String, Object> action = new HashMap<>();
        action.put("type", "MARK_STATUS");
        action.put("status", "PROCESSING");
        actionRepository.save(OmsAutoAction.builder()
                .user(userRepository.getReferenceById(userId))
                .name("Mark imported orders as PROCESSING")
                .enabled(true)
                .triggerEvent("ORDER_IMPORTED")
                .channel(null)
                .conditionJson(Map.of())
                .actionJson(action)
                .build());
        Map<String, Object> ship = new HashMap<>();
        ship.put("type", "CREATE_MEESHO_SHIPMENT");
        actionRepository.save(OmsAutoAction.builder()
                .user(userRepository.getReferenceById(userId))
                .name("Create Meesho shipment when packed (enable manually)")
                .enabled(false)
                .triggerEvent("ORDER_STATUS")
                .channel("MEESHO")
                .conditionJson(Map.of("status", "PACKED"))
                .actionJson(ship)
                .build());
    }
}
