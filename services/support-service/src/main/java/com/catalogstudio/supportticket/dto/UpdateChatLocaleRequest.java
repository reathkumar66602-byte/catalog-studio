package com.catalogstudio.supportticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateChatLocaleRequest(
        @NotBlank @Size(max = 16) String locale
) {}
