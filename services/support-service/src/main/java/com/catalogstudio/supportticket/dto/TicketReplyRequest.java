package com.catalogstudio.supportticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record TicketReplyRequest(
        @NotBlank @Size(max = 10000) String body,
        Boolean internal,
        @Size(max = 32) String status,
        List<UUID> attachmentIds
) {}
