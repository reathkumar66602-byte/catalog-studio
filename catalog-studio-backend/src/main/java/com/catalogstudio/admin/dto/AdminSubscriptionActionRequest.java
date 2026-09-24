package com.catalogstudio.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminSubscriptionActionRequest(
        @NotBlank String action,
        String planName,
        String reference,
        String notes
) {}
