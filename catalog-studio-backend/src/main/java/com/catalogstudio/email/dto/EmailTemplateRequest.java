package com.catalogstudio.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EmailTemplateRequest(
        @NotBlank @Size(max = 80) String slug,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank String htmlBody,
        String textBody,
        List<String> variables,
        Boolean enabled
) {}
