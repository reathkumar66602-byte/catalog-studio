package com.catalogstudio.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Flipkart Seller API credentials (not the same as logging into seller.flipkart.com).
 * Sellers create an app in Flipkart Seller API / developer settings and paste App ID + Secret here.
 */
public record FlipkartSellerConnectRequest(
        @NotBlank @Size(max = 120) String appId,
        @NotBlank @Size(max = 200) String appSecret,
        @Size(max = 80) String locationId,
        @Size(max = 120) String accountLabel
) {}
