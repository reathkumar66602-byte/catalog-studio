package com.catalogstudio.wholesale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Provide either categoryId or categorySlug. */
public record ProductCreateRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 60) String code,
        UUID categoryId,
        @Size(max = 140) String categorySlug,
        @Size(max = 8000) String description,
        @Size(max = 8000) String details,
        @DecimalMin("0.00") BigDecimal mrp,
        @NotEmpty @Size(max = 200) @Valid List<VariantInput> variants
) {}