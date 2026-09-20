package com.catalogstudio.product.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record TemplateRequest(
        @NotBlank String name,
        @NotBlank String marketplace,
        String productType,
        Map<String, Object> attributes
) {}
