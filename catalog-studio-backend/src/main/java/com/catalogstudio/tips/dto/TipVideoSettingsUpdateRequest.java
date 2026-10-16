package com.catalogstudio.tips.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TipVideoSettingsUpdateRequest(
        @NotNull @Min(1) @Max(50) Integer maxCount,
        @NotNull @Min(1_048_576L) @Max(10_737_418_240L) Long maxTotalBytes
) {}
