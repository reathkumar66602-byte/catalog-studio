package com.catalogstudio.marketplace.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.inventory.entity.InventoryLevel;
import com.catalogstudio.inventory.entity.ProductVariant;
import com.catalogstudio.inventory.repository.InventoryLevelRepository;
import com.catalogstudio.inventory.repository.ProductVariantRepository;
import com.catalogstudio.marketplace.client.AmazonSpApiClient;
import com.catalogstudio.marketplace.client.FlipkartSellerApiClient;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.entity.MarketplaceListing;
import com.catalogstudio.marketplace.meesho.MeeshoStockUpdate;
import com.catalogstudio.marketplace.oms.OmsChannel;
import com.catalogstudio.marketplace.repository.MarketplaceConnectionRepository;
import com.catalogstudio.marketplace.repository.MarketplaceListingRepository;
import com.catalogstudio.security.SecurityUtils;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Create/update marketplace listings from CS variants + push price/stock. */
@Service
@RequiredArgsConstructor
public class ListingSyncService {

    private final MarketplaceListingRepository listingRepository;
    private final MarketplaceConnectionRepository connectionRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;
    private final FlipkartSellerApiClient flipkartSellerApiClient;
    private final AmazonSpApiClient amazonSpApiClient;
    private final OmsAutoActionService autoActionService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return listingRepository.findAllForUser(SecurityUtils.currentUserId()).stream()
                .map(this::toMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> syncVariantToChannel(UUID variantUuid, String channel, BigDecimal price) {
        Long userId = SecurityUtils.currentUserId();
        OmsChannel ch = OmsChannel.valueOf(channel.trim().toUpperCase());
        ProductVariant variant = variantRepository.findByUuidAndUserId(variantUuid, userId)
                .orElseThrow(() -> ApiException.notFound("Variant not found"));
        MarketplaceConnection connection = connectionRepository.findByUserIdAndMarketplace(userId, ch.name())
                .orElseThrow(() -> ApiException.badRequest("Connect/sync " + ch.name() + " OMS first"));

        InventoryLevel level = levelRepository.findByVariantId(variant.getId())
                .orElse(null);
        int stock = level == null ? 0 : Math.max(0, level.getOnHand() - level.getReserved());
        BigDecimal listingPrice = price != null ? price : variant.getPrice();

        MarketplaceListing listing = listingRepository.findByVariantIdAndConnectionId(variant.getId(), connection.getId())
                .orElseGet(() -> MarketplaceListing.builder()
                        .variant(variant)
                        .connection(connection)
                        .build());
        listing.setExternalSku(variant.getSku());
        listing.setPrice(listingPrice);
        listing.setStockMirror(stock);
        listing.setStatus("LIVE");
        listing.setLastSyncedAt(Instant.now());
        listing.setLastError(null);

        try {
            List<MeeshoStockUpdate> updates = List.of(new MeeshoStockUpdate(variant.getSku(), stock));
            int pushed = switch (ch) {
                case FLIPKART -> flipkartSellerApiClient.pushStock(updates);
                case AMAZON -> amazonSpApiClient.pushStock(updates);
                case MEESHO -> 0;
            };
            if (listingPrice != null) {
                Map<String, Double> prices = Map.of(variant.getSku(), listingPrice.doubleValue());
                autoActionService.syncPrices(userId, ch.name(), prices);
            }
            if (pushed == 0 && ch != OmsChannel.MEESHO) {
                listing.setLastError("Stock push returned 0 — check location/seller id and listing SKU on channel");
                listing.setStatus("ERROR");
            }
        } catch (Exception ex) {
            listing.setStatus("ERROR");
            listing.setLastError(ex.getMessage());
        }
        listingRepository.save(listing);
        return toMap(listing);
    }

    private Map<String, Object> toMap(MarketplaceListing listing) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", listing.getUuid());
        map.put("sku", listing.getExternalSku());
        map.put("marketplace", listing.getConnection().getMarketplace());
        map.put("price", listing.getPrice());
        map.put("stockMirror", listing.getStockMirror());
        map.put("status", listing.getStatus());
        map.put("lastSyncedAt", listing.getLastSyncedAt());
        map.put("lastError", listing.getLastError());
        return map;
    }
}
