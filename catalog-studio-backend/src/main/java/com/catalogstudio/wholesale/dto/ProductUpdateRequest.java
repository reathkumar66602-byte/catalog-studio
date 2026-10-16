package com.catalogstudio.wholesale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductUpdateRequest(
        @Size(max = 255) String title,
        UUID categoryId,
        @Size(max = 140) String categorySlug,
        @Size(max = 8000) String description,
        @Size(max = 8000) String details,
        @DecimalMin("0.00") BigDecimal mrp
) {}