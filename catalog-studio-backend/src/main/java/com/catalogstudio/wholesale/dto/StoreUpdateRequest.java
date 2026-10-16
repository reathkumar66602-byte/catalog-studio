package com.catalogstudio.wholesale.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Store name and GSTIN are fixed after GST verification. */
public record StoreUpdateRequest(
        @Size(max = 4000) String description,
        @Size(max = 120) String city,
        @Size(max = 120) String state,
        @Pattern(regexp = "^$|^[+0-9][0-9 \\-]{7,18}$", message = "Enter a valid phone number") String phone,
        @Email @Size(max = 255) String email,
        List<UUID> categoryIds
) {}