package com.catalogstudio.admin.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record FeatureAccessUpdateRequest(
        @NotNull Map<String, Boolean> features
) {}
