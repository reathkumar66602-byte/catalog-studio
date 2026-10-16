package com.catalogstudio.marketplace.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.marketplace.crypto.CredentialCrypto;
import com.catalogstudio.marketplace.dto.AmazonSpApiConnectRequest;
import com.catalogstudio.marketplace.dto.AmazonSpApiConnectionResponse;
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
 * Stores Amazon SP-API LWA credentials for later orders/inventory sync.
 * Live SP-API calls are not enabled until the HTTP client is wired.
 */
@Service
@RequiredArgsConstructor
public class AmazonSpApiConnectionService {

    public static final String MARKETPLACE = "AMAZON";
    public static final String TYPE_SP_API = "SP_API";
    /** Amazon.in marketplace id */
    public static final String DEFAULT_MARKETPLACE_ID = "A21TJRUUN4KGV";

    private final MarketplaceConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final CredentialCrypto credentialCrypto;

    @Transactional(readOnly = true)
    public AmazonSpApiConnectionResponse status(Long userId) {
        return connectionRepository.findByUserIdAndMarketplace(userId, MARKETPLACE)
                .filter(c -> TYPE_SP_API.equalsIgnoreCase(c.getConnectionType()))
                .map(this::toResponse)
                .orElseGet(() -> new AmazonSpApiConnectionResponse(
                        null,
                        MARKETPLACE,
                        TYPE_SP_API,
                        ConnectionStatus.NOT_CONNECTED.name(),
                        null,
                        null,
                        DEFAULT_MARKETPLACE_ID,
                        false,
                        false,
                        true,
                        "Create a private SP-API app in Seller Central → Apps & Services → Develop Apps. "
                                + "Paste LWA Client ID, Client Secret, and Refresh Token here.",
                        null));
    }

    @Transactional
    public AmazonSpApiConnectionResponse connect(Long userId, AmazonSpApiConnectRequest request) {
        String clientId = request.clientId().trim();
        String clientSecret = request.clientSecret().trim();
        String refreshToken = request.refreshToken().trim();
        if (clientId.length() < 8 || clientSecret.length() < 8) {
            throw ApiException.badRequest("Client ID / Secret look too short. Use LWA credentials from Develop Apps.");
        }
        if (refreshToken.length() < 20) {
            throw ApiException.badRequest("Refresh token looks too short. Authorize the app in Seller Central first.");
        }

        boolean sandbox = request.sandbox() == null || request.sandbox();
        String marketplaceId = StringUtils.hasText(request.marketplaceId())
                ? request.marketplaceId().trim()
                : DEFAULT_MARKETPLACE_ID;

        Map<String, Object> config = new HashMap<>();
        config.put("clientId", clientId);
        config.put("clientSecretEnc", credentialCrypto.encrypt(clientSecret));
        config.put("refreshTokenEnc", credentialCrypto.encrypt(refreshToken));
        config.put("marketplaceId", marketplaceId);
        config.put("sandbox", sandbox);
        config.put("accountLabel", StringUtils.hasText(request.accountLabel())
                ? request.accountLabel().trim()
                : "Amazon main");
        config.put("syncEnabled", false);
        config.put("syncNote", sandbox
                ? "Credentials saved (Sandbox). Stock/order sync turns on after SP-API client is enabled."
                : "Credentials saved (Production). Stock/order sync turns on after SP-API client is enabled.");

        MarketplaceConnection connection = connectionRepository.findByUserIdAndMarketplace(userId, MARKETPLACE)
                .orElseGet(() -> MarketplaceConnection.builder()
                        .user(userRepository.getReferenceById(userId))
                        .marketplace(MARKETPLACE)
                        .build());
        connection.setConnectionType(TYPE_SP_API);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setConfigurationJson(config);
        connection.setLastActivityAt(Instant.now());
        connectionRepository.save(connection);
        return toResponse(connection);
    }

    @Transactional
    public AmazonSpApiConnectionResponse disconnect(Long userId) {
        MarketplaceConnection connection = connectionRepository.findByUserIdAndMarketplace(userId, MARKETPLACE)
                .orElseThrow(() -> ApiException.notFound("No Amazon SP-API connection"));
        connection.setStatus(ConnectionStatus.NOT_CONNECTED);
        connection.setConfigurationJson(Map.of(
                "syncEnabled", false,
                "sandbox", true,
                "marketplaceId", DEFAULT_MARKETPLACE_ID,
                "syncNote", "Disconnected. Re-enter Client ID, Secret, and Refresh Token to connect again."));
        connection.setLastActivityAt(Instant.now());
        connectionRepository.save(connection);
        return toResponse(connection);
    }

    private AmazonSpApiConnectionResponse toResponse(MarketplaceConnection connection) {
        Map<String, Object> cfg = connection.getConfigurationJson() == null
                ? Map.of()
                : connection.getConfigurationJson();
        String clientId = stringVal(cfg.get("clientId"));
        boolean secret = StringUtils.hasText(stringVal(cfg.get("clientSecretEnc")))
                || StringUtils.hasText(stringVal(cfg.get("clientSecret")));
        boolean refresh = StringUtils.hasText(stringVal(cfg.get("refreshTokenEnc")))
                || StringUtils.hasText(stringVal(cfg.get("refreshToken")));
        boolean sandbox = cfg.get("sandbox") == null || boolVal(cfg.get("sandbox"));
        String marketplaceId = stringVal(cfg.get("marketplaceId"));
        if (!StringUtils.hasText(marketplaceId)) {
            marketplaceId = DEFAULT_MARKETPLACE_ID;
        }
        return new AmazonSpApiConnectionResponse(
                connection.getUuid(),
                connection.getMarketplace(),
                connection.getConnectionType(),
                connection.getStatus().name(),
                stringVal(cfg.get("accountLabel")),
                mask(clientId),
                marketplaceId,
                secret,
                refresh,
                sandbox,
                stringVal(cfg.get("syncNote")),
                connection.getLastActivityAt());
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean boolVal(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
