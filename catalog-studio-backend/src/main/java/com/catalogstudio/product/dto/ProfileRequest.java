package com.catalogstudio.product.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record ProfileRequest(
        @NotBlank String name,
        @NotBlank String marketplace,
        Map<String, Object> profileJson
) {}
