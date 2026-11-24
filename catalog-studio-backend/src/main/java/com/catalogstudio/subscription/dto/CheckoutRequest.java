package com.catalogstudio.subscription.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @Size(max = 40) String promoCode,
        /** Optional override when profile mobile is missing (10-digit Indian mobile). */
        @Size(max = 20)
        @Pattern(regexp = "^$|^[0-9+]{8,20}$", message = "Enter a valid mobile number")
        String customerPhone,
        /** Browser origin (e.g. https://www.catalogstudio.in) so Cashfree returns to the same host. */
        @Size(max = 120) String returnOrigin
) {}
