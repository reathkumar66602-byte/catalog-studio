package com.catalogstudio.marketplace.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.marketplace.crypto.CredentialCrypto;
import com.catalogstudio.marketplace.dto.MeeshoPanelConnectRequest;
import com.catalogstudio.marketplace.dto.MeeshoPanelConnectionResponse;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.entity.MarketplaceConnection.ConnectionStatus;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient.AuthResult;
import com.catalogstudio.marketplace.meesho.MeeshoPanelSession;
import com.catalogstudio.marketplace.repository.MarketplaceChannelOrderRepository;
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
 * Base.com-style Meesho connector: Supplier Panel email + password.
 * Catalog Studio remains stock source of truth; sync pushes/pulls via {@link MeeshoPanelClient}.
 */
@Service
@RequiredArgsConstructor
public class MeeshoPanelConnectionService {

    public static final String MARKETPLACE = "MEESHO";
    public static final String TYPE_PANEL_LOGIN = "PANEL_LOGIN";

    private final MarketplaceConnectionRepository connectionRepository;
    private final MarketplaceChannelOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final MeeshoPanelClient meeshoPanelClient;
    private final CredentialCrypto credentialCrypto;

    @Transactional(readOnly = true)
    public MeeshoPanelConnectionResponse status(Long userId) {
        return connectionRepository.findByUserIdAndMarketplace(userId, MARKETPLACE)
                .filter(c -> TYPE_PANEL_LOGIN.equalsIgnoreCase(c.getConnectionType()))
                .map(c -> toResponse(c, userId))
                .orElseGet(() -> new MeeshoPanelConnectionResponse(
                        null,
                        MARKETPLACE,
                        TYPE_PANEL_LOGIN,
                        ConnectionStatus.NOT_CONNECTED.name(),
                        null,
                        null,
                        false,
                        false,
                        false,
                        "Enter the same email + password you use on supplier.meesho.com "
                                + "(Base-style panel login — not a Meesho App ID).",
                        null,
                        null,
                        null,
                        null,
                        null,
                        0));
    }

    @Transactional
    public MeeshoPanelConnectionResponse connect(Long userId, MeeshoPanelConnectRequest request) {
        String email = request.email().trim().toLowerCase();
        String password = request.password();
        AuthResult auth = meeshoPanelClient.authenticate(email, password);
        if (!auth.success()) {
            throw ApiException.badRequest(auth.message() == null
                    ? "Meesho panel login failed"
                    : auth.message());
        }

        Map<String, Object> config = new HashMap<>();
        config.put("email", email);
        config.put("passwordEnc", credentialCrypto.encrypt(password));
        config.put("accountLabel", StringUtils.hasText(request.accountLabel())
                ? request.accountLabel().trim()
                : "Meesho main");
        config.put("syncEnabled", true);
        config.put("liveAuthVerified", auth.verified());
        config.put("lastAuthAt", Instant.now().toString());
        config.put("syncNote", auth.message());
        storeSession(config, auth.session());

        MarketplaceConnection connection = connectionRepository.findByUserIdAndMarketplace(userId, MARKETPLACE)
                .orElseGet(() -> MarketplaceConnection.builder()
                        .user(userRepository.getReferenceById(userId))
                        .marketplace(MARKETPLACE)
                        .build());
        connection.setConnectionType(TYPE_PANEL_LOGIN);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setConfigurationJson(config);
        connection.setLastActivityAt(Instant.now());
        connectionRepository.save(connection);
        return toResponse(connection, userId);
    }

    @Transactional
    public MeeshoPanelConnectionResponse disconnect(Long userId) {
        MarketplaceConnection connection = requirePanelConnection(userId);
        connection.setStatus(ConnectionStatus.NOT_CONNECTED);
        connection.setConfigurationJson(Map.of(
                "syncEnabled", false,
                "liveAuthVerified", false,
                "syncNote", "Disconnected. Re-enter Meesho Supplier Panel email + password to connect."));
        connection.setLastActivityAt(Instant.now());
        connectionRepository.save(connection);
        return toResponse(connection, userId);
    }

    @Transactional(readOnly = true)
    public MarketplaceConnection requirePanelConnection(Long userId) {
        return connectionRepository.findByUserIdAndMarketplace(userId, MARKETPLACE)
                .filter(c -> TYPE_PANEL_LOGIN.equalsIgnoreCase(c.getConnectionType()))
                .filter(c -> c.getStatus() == ConnectionStatus.CONNECTED)
                .orElseThrow(() -> ApiException.badRequest(
                        "Connect Meesho Supplier Panel email + password first"));
    }

    @Transactional(readOnly = true)
    public PanelCredentials loadCredentials(MarketplaceConnection connection) {
        Map<String, Object> cfg = connection.getConfigurationJson() == null
                ? Map.of()
                : connection.getConfigurationJson();
        String email = stringVal(cfg.get("email"));
        String passwordEnc = stringVal(cfg.get("passwordEnc"));
        if (!StringUtils.hasText(email) || !StringUtils.hasText(passwordEnc)) {
            throw ApiException.badRequest("Meesho panel credentials missing — reconnect");
        }
        return new PanelCredentials(email, credentialCrypto.decrypt(passwordEnc), cfg);
    }

    @Transactional
    public void saveConfig(MarketplaceConnection connection, Map<String, Object> config) {
        connection.setConfigurationJson(config);
        connection.setLastActivityAt(Instant.now());
        connectionRepository.save(connection);
    }

    public MeeshoPanelSession ensureSession(MarketplaceConnection connection) {
        PanelCredentials creds = loadCredentials(connection);
        AuthResult auth = meeshoPanelClient.authenticate(creds.email(), creds.password());
        if (!auth.success()) {
            Map<String, Object> cfg = new HashMap<>(creds.config());
            cfg.put("liveAuthVerified", false);
            cfg.put("syncNote", auth.message());
            connection.setStatus(ConnectionStatus.ERROR);
            saveConfig(connection, cfg);
            throw ApiException.badRequest(auth.message() == null
                    ? "Meesho panel session expired — update password"
                    : auth.message());
        }
        Map<String, Object> cfg = new HashMap<>(creds.config());
        cfg.put("liveAuthVerified", auth.verified());
        cfg.put("lastAuthAt", Instant.now().toString());
        cfg.put("syncNote", auth.message());
        storeSession(cfg, auth.session());
        connection.setStatus(ConnectionStatus.CONNECTED);
        saveConfig(connection, cfg);
        return auth.session();
    }

    private MeeshoPanelConnectionResponse toResponse(MarketplaceConnection connection, Long userId) {
        Map<String, Object> cfg = connection.getConfigurationJson() == null
                ? Map.of()
                : connection.getConfigurationJson();
        long pending = orderRepository.countByUserIdAndMarketplaceAndStatusIgnoreCase(
                userId, MARKETPLACE, "PENDING");
        return new MeeshoPanelConnectionResponse(
                connection.getUuid(),
                connection.getMarketplace(),
                connection.getConnectionType(),
                connection.getStatus().name(),
                stringVal(cfg.get("accountLabel")),
                maskEmail(stringVal(cfg.get("email"))),
                StringUtils.hasText(stringVal(cfg.get("passwordEnc"))),
                boolVal(cfg.get("syncEnabled")),
                boolVal(cfg.get("liveAuthVerified")),
                stringVal(cfg.get("syncNote")),
                instantVal(cfg.get("lastAuthAt")),
                instantVal(cfg.get("lastOrdersSyncAt")),
                instantVal(cfg.get("lastStockSyncAt")),
                instantVal(cfg.get("lastLabelsSyncAt")),
                connection.getLastActivityAt(),
                pending);
    }

    private static void storeSession(Map<String, Object> config, MeeshoPanelSession session) {
        if (session == null) {
            return;
        }
        if (StringUtils.hasText(session.accessToken())) {
            config.put("sessionTokenPresent", true);
        }
        if (StringUtils.hasText(session.cookieHeader())) {
            config.put("sessionCookiePresent", true);
        }
        if (session.expiresAt() != null) {
            config.put("sessionExpiresAt", session.expiresAt().toString());
        }
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

    private static Instant instantVal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
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

    public record PanelCredentials(String email, String password, Map<String, Object> config) {}
}
