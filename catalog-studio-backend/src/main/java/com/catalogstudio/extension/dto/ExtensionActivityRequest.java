package com.catalogstudio.extension.dto;

import java.util.Map;
import java.util.UUID;

public record ExtensionActivityRequest(
        String marketplace,
        @jakarta.validation.constraints.NotBlank String action,
        UUID productId,
        Map<String, Object> details
) {}
