package com.catalogstudio.marketplace.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.marketplace.MarketplaceAdapter;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.repository.MarketplaceConnectionRepository;
import com.catalogstudio.product.entity.Product;
import com.catalogstudio.product.repository.ProductRepository;
import com.catalogstudio.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private final List<MarketplaceAdapter> adapters;
    private final MarketplaceConnectionRepository connectionRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public List<Map<String, Object>> catalog(Long userId) {
        Map<String, MarketplaceConnection> byMarketplace = connectionRepository.findByUserId(userId).stream()
                .collect(java.util.stream.Collectors.toMap(MarketplaceConnection::getMarketplace, c -> c, (a, b) -> a));
        return adapters.stream().map(adapter -> {
            MarketplaceConnection connection = byMarketplace.get(adapter.getMarketplace());
            Map<String, Object> card = new java.util.LinkedHashMap<>();
            card.put("marketplace", adapter.getMarketplace());
            card.put("supportedFields", adapter.getSupportedFields());
            card.put("features", List.of("AI Listing", "Autofill", "Profiles"));
            card.put("status", connection == null ? "NOT_CONNECTED" : connection.getStatus().name());
            card.put("connectionType", connection == null ? "EXTENSION" : connection.getConnectionType());
            card.put("lastActivityAt", connection == null ? null : connection.getLastActivityAt());
            return card;
        }).toList();
    }

    @Transactional
    public MarketplaceConnection configure(Long userId, String marketplace) {
        MarketplaceAdapter adapter = adapter(marketplace);
        return connectionRepository.findByUserIdAndMarketplace(userId, adapter.getMarketplace())
                .map(existing -> {
                    existing.setStatus(MarketplaceConnection.ConnectionStatus.CONNECTED);
                    existing.setLastActivityAt(Instant.now());
                    return existing;
                })
                .orElseGet(() -> connectionRepository.save(MarketplaceConnection.builder()
                        .user(userRepository.getReferenceById(userId))
                        .marketplace(adapter.getMarketplace())
                        .connectionType("EXTENSION")
                        .status(MarketplaceConnection.ConnectionStatus.CONNECTED)
                        .lastActivityAt(Instant.now())
                        .build()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> prepare(Long userId, UUID productId, String marketplace) {
        Product product = productRepository.findByUuidAndUserId(productId, userId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        return adapter(marketplace).prepareListing(product);
    }

    private MarketplaceAdapter adapter(String marketplace) {
        String key = marketplace == null ? "" : marketplace.toUpperCase();
        return adapters.stream()
                .filter(a -> a.getMarketplace().equals(key))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Unsupported marketplace"));
    }
}
