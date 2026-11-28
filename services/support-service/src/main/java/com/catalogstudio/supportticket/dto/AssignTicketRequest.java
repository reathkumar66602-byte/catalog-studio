package com.catalogstudio.supportticket.dto;

import jakarta.validation.constraints.Size;

public record AssignTicketRequest(
        /** When null, assign uses the authenticated staff user. */
        Long adminUserId,
        @Size(max = 160) String adminName
) {}
