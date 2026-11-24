package com.catalogstudio.wholesale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Supplier edits an issued bill (buyer details, prices, GST) then PDF is regenerated. */
public record BillEditRequest(
        @NotBlank @Size(max = 160) String buyerName,
        @NotBlank @Size(max = 20) String buyerMobile,
        @Email @Size(max = 255) String buyerEmail,
        @Size(max = 20) String buyerGstin,
        @Size(max = 500) String buyerAddress,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal gstPercent,
        @Size(max = 1000) String supplierNote,
        @NotEmpty @Size(max = 200) @Valid List<Line> lines
) {
    public record Line(
            @NotNull UUID itemId,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @Size(max = 16) String hsnSac
    ) {}
}
