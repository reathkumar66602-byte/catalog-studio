package com.catalogstudio.supportticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        Boolean active,
        Integer sortOrder
) {}
