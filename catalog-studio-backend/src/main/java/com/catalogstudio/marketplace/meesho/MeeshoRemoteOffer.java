package com.catalogstudio.marketplace.meesho;

import java.util.Map;

/** Meesho supplier-panel listing/offer row used for Base-style offer linking. */
public record MeeshoRemoteOffer(
        String externalListingId,
        String sku,
        String title,
        Integer stock,
        Double price,
        String status,
        Map<String, Object> raw
) {}
