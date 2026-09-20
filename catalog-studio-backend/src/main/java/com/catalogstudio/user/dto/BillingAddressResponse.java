package com.catalogstudio.user.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingAddressResponse(
        UUID id,
        String businessName,
        String gstNumber,
        String address,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        String landmark,
        String googlePlaceId,
        BigDecimal latitude,
        BigDecimal longitude
) {}
