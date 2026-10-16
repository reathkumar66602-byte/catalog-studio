package com.catalogstudio.wholesale.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record StoreCreateRequest(
        /** Ignored on create — server sets store name from GST trade/legal name. */
        @Size(max = 160) String storeName,
        @NotBlank @Size(min = 15, max = 15, message = "GSTIN must be 15 characters") String gstin,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(max = 120) String state,
        @NotBlank @Pattern(regexp = "^[+0-9][0-9 \\-]{7,18}$", message = "Enter a valid phone number") String phone,
        @NotBlank @Email @Size(max = 255) String email,
        List<UUID> categoryIds
) {}