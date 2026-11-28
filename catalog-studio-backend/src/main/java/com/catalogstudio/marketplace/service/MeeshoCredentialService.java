package com.catalogstudio.marketplace.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.marketplace.crypto.CredentialCrypto;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.entity.MarketplaceConnection.ConnectionStatus;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient.AuthResult;
import com.catalogstudio.marketplace.meesho.MeeshoPanelSession;
import com.catalogstudio.marketplace.repository.MarketplaceConnectionRepository;
import com.catalogstudio.user.repository.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Base.com-style Meesho credentials: each Catalog Studio user connects their own Supplier Panel
 * account. Property-file credentials are only used in optional single-shop mode
 * ({@code prefer-property-credentials=true}).
 */
@Service
@RequiredArgsConstructor
public class MeeshoCredentialService {

    private final CatalogStudioProperties properties;
    private final MarketplaceConnectionRepository connectionRepository;
    private final MeeshoPanelClient meeshoPanelClient;
    private final CredentialCrypto credentialCrypto;

    public record ResolvedCredentials(String email, String password, String source) {}

    public boolean hasUserConnection(Long userId) {
        return findUserConnection(userId).isPresent();
    }

    public ResolvedCredentials resolve(Long userId) {
        CatalogStudioProperties.Oms.Meesho cfg = properties.oms().meesho();

        // Multi-user (default): this seller's saved Meesho login only.
        Optional<ResolvedCredentials> own = credentialsFromConnection(findUserConnection(userId).orElse(null));
        if (own.isPresent()) {
            return own.get();
        }

        // Optional single-shop mode: one Meesho account in the property file for the whole app.
        if (cfg.preferPropertyCredentials() && cfg.configured()) {
            return new ResolvedCredentials(cfg.email().trim(), cfg.password(), "PROPERTY_FILE");
        }

        throw ApiException.badRequest(
                "Connect your own Meesho Supplier Panel email and password first "
                        + "(each seller uses their own Meesho account, like Base.com).");
    }

    public AuthResult authenticate(Long userId) {
        ResolvedCredentials creds = resolve(userId);
        return meeshoPanelClient.authenticate(creds.email(), creds.password());
    }

    public MeeshoPanelSession requireSession(Long userId) {
        AuthResult auth = authenticate(userId);
        if (!auth.success()) {
            throw ApiException.badRequest(auth.message() == null ? "Meesho login failed" : auth.message());
        }
        return auth.session();
    }

    /**
     * Returns the seller's Meesho connection. Does not invent a connection from the global
     * property file (that would leak one Meesho shop into every user).
     */
    @Transactional
    public MarketplaceConnection requireConnection(Long userId, UserRepository users) {
        Optional<MarketplaceConnection> existing = findUserConnection(userId);
        if (existing.isPresent()) {
            return existing.get();
        }

        CatalogStudioProperties.Oms.Meesho cfg = properties.oms().meesho();
        if (cfg.preferPropertyCredentials() && cfg.configured()) {
            MarketplaceConnection connection = connectionRepository
                    .findByUserIdAndMarketplace(userId, MeeshoPanelConnectionService.MARKETPLACE)
                    .orElseGet(() -> MarketplaceConnection.builder()
                            .user(users.getReferenceById(userId))
                            .marketplace(MeeshoPanelConnectionService.MARKETPLACE)
                            .build());
            connection.setConnectionType(MeeshoPanelConnectionService.TYPE_PANEL_LOGIN);
            connection.setStatus(ConnectionStatus.CONNECTED);
            Map<String, Object> json = new HashMap<>();
            json.put("credentialSource", "PROPERTY_FILE");
            json.put("syncEnabled", true);
            json.put("email", cfg.email().trim());
            json.put("passwordEnc", credentialCrypto.encrypt(cfg.password()));
            json.put("accountLabel", "Property-file Meesho (single-shop mode)");
            connection.setConfigurationJson(json);
            return connectionRepository.save(connection);
        }

        throw ApiException.badRequest(
                "Connect your Meesho Supplier Panel account before syncing. "
                        + "Each seller must use their own Meesho email/password.");
    }

    /** @deprecated use {@link #requireConnection(Long, UserRepository)} — kept for call-site compatibility. */
    @Transactional
    public MarketplaceConnection ensureConnection(Long userId, UserRepository users) {
        return requireConnection(userId, users);
    }

    private Optional<MarketplaceConnection> findUserConnection(Long userId) {
        return connectionRepository
                .findByUserIdAndMarketplace(userId, MeeshoPanelConnectionService.MARKETPLACE)
                .filter(c -> MeeshoPanelConnectionService.TYPE_PANEL_LOGIN.equalsIgnoreCase(c.getConnectionType()))
                .filter(c -> c.getStatus() == ConnectionStatus.CONNECTED)
                .filter(c -> credentialsFromConnection(c).isPresent());
    }

    private Optional<ResolvedCredentials> credentialsFromConnection(MarketplaceConnection connection) {
        if (connection == null) {
            return Optional.empty();
        }
        Map<String, Object> json = connection.getConfigurationJson() == null
                ? Map.of()
                : connection.getConfigurationJson();
        String email = stringVal(json.get("email"));
        String passwordEnc = stringVal(json.get("passwordEnc"));
        if (!StringUtils.hasText(email) || !StringUtils.hasText(passwordEnc)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ResolvedCredentials(
                    email, credentialCrypto.decrypt(passwordEnc), "CONNECTION_DB"));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
