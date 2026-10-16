package com.catalogstudio.wholesale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GstLookupRequest(
        @NotBlank @Size(min = 15, max = 15, message = "GSTIN must be 15 characters") String gstin
) {}
