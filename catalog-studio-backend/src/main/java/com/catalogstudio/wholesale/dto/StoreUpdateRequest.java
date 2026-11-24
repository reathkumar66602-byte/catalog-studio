package com.catalogstudio.wholesale.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Store name and GSTIN are fixed after GST verification. */
public record StoreUpdateRequest(
        @Size(max = 4000) String description,
        @Size(max = 120) String city,
        @Size(max = 120) String state,
        @Size(max = 500) String addressLine,
        @Size(max = 12) String pincode,
        @Size(max = 20) String pan,
        @Pattern(regexp = "^$|^[+0-9][0-9 \\-]{7,18}$", message = "Enter a valid phone number") String phone,
        @Email @Size(max = 255) String email,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal gstPercent,
        @Size(max = 160) String bankAccountName,
        @Size(max = 40) String bankAccountNumber,
        @Size(max = 120) String bankName,
        @Size(max = 20) String bankIfsc,
        @Size(max = 120) String bankBranch,
        @Size(max = 4000) String invoiceTerms,
        List<UUID> categoryIds
) {}
