package com.catalogstudio.wholesale.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Phone is always exposed for public store pages.
 * Owner-only fields (gstin, email, GST status) are null on public listings.
 */
public record StoreResponse(
        UUID id,
        String storeName,
        String slug,
        String description,
        String city,
        String state,
        String addressLine,
        String pincode,
        String pan,
        String status,
        int productCount,
        List<CategoryResponse> categories,
        String gstin,
        String gstLegalName,
        String gstVerificationStatus,
        Instant gstVerifiedAt,
        String phone,
        String email,
        BigDecimal gstPercent,
        String bankAccountName,
        String bankAccountNumber,
        String bankName,
        String bankIfsc,
        String bankBranch,
        String invoiceTerms
) {}
