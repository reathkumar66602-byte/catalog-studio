package com.catalogstudio.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 120) String name,
        @Size(max = 20) String mobile,
        @Size(max = 200) String businessName,
        @Size(max = 32) String gstNumber,
        String address,
        String preferredAiProvider,
        @Size(max = 16) String preferredLocale
) {}
