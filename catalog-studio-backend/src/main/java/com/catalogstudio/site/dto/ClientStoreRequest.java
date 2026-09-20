package com.catalogstudio.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ClientStoreRequest(
        @NotBlank @Size(max = 200) String storeName,
        @NotBlank @Size(max = 80) String slug,
        @Size(max = 120) String ownerName,
        @Size(max = 255) String email,
        @Size(max = 40) String phone,
        String address,
        @Size(max = 500) String websiteUrl,
        @Size(max = 500) String logoUrl,
        @Size(max = 255) String tagline,
        String about,
        Map<String, Object> branding,
        boolean featured,
        @Size(max = 32) String status
) {}
