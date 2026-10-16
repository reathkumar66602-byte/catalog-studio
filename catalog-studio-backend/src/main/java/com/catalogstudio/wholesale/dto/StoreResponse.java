package com.catalogstudio.wholesale.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Owner-only fields (gstin, phone, email, GST status) are null on public listings. */
public record StoreResponse(
        UUID id,
        String storeName,
        String slug,
        String description,
        String city,
        String state,
        String status,
        int productCount,
        List<CategoryResponse> categories,
        String gstin,
        String gstLegalName,
        String gstVerificationStatus,
        Instant gstVerifiedAt,
        String phone,
        String email
) {}