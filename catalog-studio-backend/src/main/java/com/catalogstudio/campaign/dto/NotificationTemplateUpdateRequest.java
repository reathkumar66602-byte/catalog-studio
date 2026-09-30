package com.catalogstudio.campaign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NotificationTemplateUpdateRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 255) String subject,
        @NotBlank String bodyText,
        String bodyHtml,
        List<String> variables,
        Boolean enabled
) {}
