package com.catalogstudio.user.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BillingAddressRequest(
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 120) String city,
        @Size(max = 120) String state,
        @Size(max = 20) String postalCode,
        @Size(max = 80) String country,
        @Size(max = 160) String landmark,
        @Size(max = 255) String googlePlaceId,
        BigDecimal latitude,
        BigDecimal longitude,
        @Size(max = 32) String gstNumber
) {}
