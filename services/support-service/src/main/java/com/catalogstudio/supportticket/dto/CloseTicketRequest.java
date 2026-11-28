package com.catalogstudio.supportticket.dto;

import jakarta.validation.constraints.Size;

public record CloseTicketRequest(@Size(max = 5000) String resolutionSummary) {}
