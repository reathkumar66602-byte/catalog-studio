package com.catalogstudio.marketplace.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Meesho Supplier Panel login (same email/password as supplier.meesho.com).
 * Base.com-style connector — not partner API App ID/Secret.
 */
public record MeeshoPanelConnectRequest(
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(min = 6, max = 120) String password,
        @Size(max = 120) String accountLabel
) {}
