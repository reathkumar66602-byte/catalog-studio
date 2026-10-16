package com.catalogstudio.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Amazon Selling Partner API (SP-API) credentials.
 * Private app for your own Seller Central account: LWA client id/secret + refresh token.
 */
public record AmazonSpApiConnectRequest(
        @NotBlank @Size(max = 200) String clientId,
        @NotBlank @Size(max = 200) String clientSecret,
        @NotBlank @Size(max = 800) String refreshToken,
        @Size(max = 40) String marketplaceId,
        @Size(max = 120) String accountLabel,
        Boolean sandbox
) {}
