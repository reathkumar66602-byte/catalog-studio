package com.catalogstudio.wholesale.dto;

import java.util.UUID;

/**
 * AI suggestions for the supplier "new product" form.
 * Does not create a catalog or wholesale product — form fill only.
 */
public record ProductAutofillResponse(
        String title,
        String description,
        String details,
        UUID categoryId,
        String categoryName,
        String primaryColor,
        String colorHex,
        String productType,
        Double overallConfidence
) {}
