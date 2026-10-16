package com.catalogstudio.marketplace.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.marketplace.dto.FlipkartSellerConnectRequest;
import com.catalogstudio.marketplace.dto.SellerConnectionResponse;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.entity.MarketplaceConnection.ConnectionStatus;
import com.catalogstudio.marketplace.repository.MarketplaceConnectionRepository;
import com.catalogstudio.user.repository.UserRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Stores Flipkart Seller API credentials for later stock/order sync.
 * Live Flipkart API calls are not enabled until the seller's app is approved
 * and we wire the HTTP client — this is scaffolding only.
 */
@Service
@RequiredArgsConstructor
public class FlipkartSellerConnectionService {

    public static final String MARKETPLACE = "FLIPKART";
    public static final String TYPE_SELLER_API = "SELLER_API";

    private final MarketplaceConnectionRepository connectionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public SellerConnectionResponse status(Long userId) {
        return connectionRepository.findByUserIdAndMarketplace(userId, MARKETPLACE)
                .filter(c -> TYPE_SELLER_API.equalsIgnoreCase(c.getConnectionType()))
                .map(this::toResponse)
                .orElseGet(() -> new SellerConnectionResponse(
                        null,
                        MARKETPLACE,
                        TYPE_SELLER_API,
                        ConnectionStatus.NOT_CONNECTED.name(),
                        null,
                        null,
                        null,
                        false,
                        "Paste Flipkart Seller API App ID + Secret after you create an API app. "
                                + "Seller panel login alone is not enough.",
                        null));
    }

    @Transactional
    public SellerConnectionResponse connect(Long userId, FlipkartSellerConnectRequest request) {
        String appId = request.appId().trim();
        String secret = request.appSecret().trim();
        if (appId.length() < 4 || secret.length() < 8) {
            throw ApiException.badRequest("App ID / Secret look too short. Use credentials from Flipkart Seller API.");
        }

        Map<String, Object> config = new HashMap<>();
        config.put("appId", appId);
        config.put("appSecret", secret);
        if (StringUtils.hasText(request.locationId())) {
            config.put("locationId", request.locationId().trim());
        }
        if (StringUtils.hasText(request.accountLabel())) {
            config.put("accountLabel", request.accountLabel().trim());
        }
        config.put("syncEnabled", false);
        config.put("syncNote", "Credentials saved. Stock/order sync will turn on after Flipkart API client is enabled.");

        MarketplaceConnection connection = connectionRepository.findByUserIdAndMarketplace(userId, MARKETPLACE)
                .orElseGet(() -> MarketplaceConnection.builder()
                        .user(userRepository.getReferenceById(userId))
                        .marketplace(MARKETPLACE)
                        .build());
        connection.setConnectionType(TYPE_SELLER_API);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setConfigurationJson(config);
        connection.setLastActivityAt(Instant.now());
        connectionRepository.save(connection);
        return toResponse(connection);
    }

    @Transactional
    public SellerConnectionResponse disconnect(Long userId) {
        MarketplaceConnection connection = connectionRepository.findByUserIdAndMarketplace(userId, MARKETPLACE)
                .orElseThrow(() -> ApiException.notFound("No Flipkart seller API connection"));
        connection.setStatus(ConnectionStatus.NOT_CONNECTED);
        connection.setConfigurationJson(Map.of(
                "syncEnabled", false,
                "syncNote", "Disconnected. Re-enter App ID + Secret to connect again."));
        connection.setLastActivityAt(Instant.now());
        connectionRepository.save(connection);
        return toResponse(connection);
    }

    private SellerConnectionResponse toResponse(MarketplaceConnection connection) {
        Map<String, Object> cfg = connection.getConfigurationJson() == null
                ? Map.of()
                : connection.getConfigurationJson();
        String appId = stringVal(cfg.get("appId"));
        String secret = stringVal(cfg.get("appSecret"));
        return new SellerConnectionResponse(
                connection.getUuid(),
                connection.getMarketplace(),
                connection.getConnectionType(),
                connection.getStatus().name(),
                stringVal(cfg.get("accountLabel")),
                mask(appId),
                stringVal(cfg.get("locationId")),
                StringUtils.hasText(secret),
                stringVal(cfg.get("syncNote")),
                connection.getLastActivityAt());
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String mask(String appId) {
        if (!StringUtils.hasText(appId)) {
            return null;
        }
        if (appId.length() <= 4) {
            return "****";
        }
        return appId.substring(0, 2) + "****" + appId.substring(appId.length() - 2);
    }
}
