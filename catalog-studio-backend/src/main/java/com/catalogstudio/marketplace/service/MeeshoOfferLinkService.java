package com.catalogstudio.marketplace.service;

import com.catalogstudio.inventory.entity.InventoryLevel;
import com.catalogstudio.inventory.entity.ProductVariant;
import com.catalogstudio.inventory.repository.InventoryLevelRepository;
import com.catalogstudio.inventory.repository.ProductVariantRepository;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.entity.MarketplaceListing;
import com.catalogstudio.marketplace.meesho.MeeshoPanelSession;
import com.catalogstudio.marketplace.meesho.MeeshoRemoteOffer;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient;
import com.catalogstudio.marketplace.repository.MarketplaceListingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Imports Meesho offers and links them to Catalog Studio variants by SKU (Base offer link).
 */
@Service
@RequiredArgsConstructor
public class MeeshoOfferLinkService {

    private final MeeshoPanelClient meeshoPanelClient;
    private final MarketplaceListingRepository listingRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;

    @Transactional
    public Map<String, Object> importAndLink(Long userId, MarketplaceConnection connection, MeeshoPanelSession session) {
        List<MeeshoRemoteOffer> offers = meeshoPanelClient.fetchOffers(session);
        int linked = 0;
        int unmatched = 0;
        int updated = 0;
        List<String> unmatchedSkus = new ArrayList<>();
        for (MeeshoRemoteOffer offer : offers) {
            String sku = StringUtils.hasText(offer.sku()) ? offer.sku().trim() : null;
            if (!StringUtils.hasText(sku)) {
                unmatched++;
                continue;
            }
            List<ProductVariant> variants = variantRepository.findAllByUserIdAndSkuIgnoreCase(userId, sku);
            if (variants.isEmpty()) {
                unmatched++;
                if (unmatchedSkus.size() < 25) {
                    unmatchedSkus.add(sku);
                }
                continue;
            }
            ProductVariant variant = variants.get(0);
            MarketplaceListing listing = listingRepository
                    .findByVariantIdAndConnectionId(variant.getId(), connection.getId())
                    .orElseGet(() -> MarketplaceListing.builder()
                            .variant(variant)
                            .connection(connection)
                            .build());
            boolean existed = listing.getId() != null;
            listing.setExternalSku(sku);
            listing.setExternalListingId(StringUtils.hasText(offer.externalListingId())
                    ? offer.externalListingId()
                    : sku);
            listing.setOfferTitle(offer.title());
            if (offer.price() != null) {
                listing.setPrice(BigDecimal.valueOf(offer.price()));
            }
            InventoryLevel level = levelRepository.findByVariantId(variant.getId()).orElse(null);
            int available = level == null ? 0 : Math.max(0, level.getOnHand() - level.getReserved());
            listing.setStockMirror(offer.stock() != null ? offer.stock() : available);
            listing.setStatus(normalizeStatus(offer.status()));
            listing.setLastSyncedAt(Instant.now());
            listing.setLastError(null);
            listingRepository.save(listing);
            if (existed) {
                updated++;
            } else {
                linked++;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("offersFetched", offers.size());
        result.put("linked", linked);
        result.put("updated", updated);
        result.put("unmatched", unmatched);
        result.put("unmatchedSkus", unmatchedSkus);
        result.put("message", offers.isEmpty()
                ? "No Meesho offers returned (panel catalog route may need mapping). SKU links still work when stock is pushed."
                : "Linked " + linked + " new / updated " + updated + " offers; " + unmatched + " unmatched SKU(s).");
        return result;
    }

    @Transactional(readOnly = true)
    public List<MarketplaceListing> linkedLiveListings(Long userId, Long connectionId) {
        return listingRepository.findLinkedForUserAndConnection(userId, connectionId);
    }

    private static String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "LIVE";
        }
        String s = status.trim().toUpperCase(Locale.ROOT);
        if (s.contains("PAUSE") || s.contains("INACTIVE") || s.contains("END")) {
            return "PAUSED";
        }
        if (s.contains("ERROR") || s.contains("FAIL")) {
            return "ERROR";
        }
        if (s.contains("DRAFT")) {
            return "DRAFT";
        }
        return "LIVE";
    }
}
