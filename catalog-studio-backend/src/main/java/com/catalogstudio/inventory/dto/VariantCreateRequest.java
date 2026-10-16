package com.catalogstudio.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record VariantCreateRequest(
        @NotBlank @Size(max = 80) String sku,
        @Size(max = 40) String sizeLabel,
        @Size(max = 80) String colorLabel,
        @Size(max = 80) String barcode,
        BigDecimal price,
        Integer initialOnHand
) {}
