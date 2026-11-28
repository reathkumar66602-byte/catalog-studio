package com.catalogstudio.supportticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateTicketRequest(
        UUID sessionUuid,
        @NotBlank @Size(max = 255) String subject,
        @NotBlank @Size(max = 10000) String description,
        @Size(max = 64) String categoryCode,
        @Size(max = 16) String priority,
        @Size(max = 16) String locale,
        List<UUID> attachmentIds
) {}
