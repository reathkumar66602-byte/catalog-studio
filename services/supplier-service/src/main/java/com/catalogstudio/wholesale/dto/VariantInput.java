package com.catalogstudio.wholesale.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record VariantInput(
        @Size(max = 120) String sku,
        @Size(max = 80) String colorLabel,
        @Size(max = 40) String sizeLabel,
        @Pattern(regexp = "^$|^#?[0-9A-Fa-f]{3,8}$", message = "Colour hex must look like #AABBCC") String colorHex,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @DecimalMin("0.00") BigDecimal mrp,
        @Min(0) int stock
) {}