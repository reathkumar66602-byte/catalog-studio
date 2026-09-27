package com.catalogstudio.subscription.dto;

import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @Size(max = 40) String promoCode
) {}
