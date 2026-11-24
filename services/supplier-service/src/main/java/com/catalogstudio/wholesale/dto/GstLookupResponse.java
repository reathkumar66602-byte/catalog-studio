package com.catalogstudio.wholesale.dto;

/**
 * GST registry lookup used while opening a wholesale store.
 * {@code storeName} is trade name when present, otherwise legal name — UI locks this value.
 */
public record GstLookupResponse(
        String gstin,
        String legalName,
        String tradeName,
        String storeName,
        String status
) {}
