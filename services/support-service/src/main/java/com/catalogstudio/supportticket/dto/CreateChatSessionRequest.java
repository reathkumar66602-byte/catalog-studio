package com.catalogstudio.supportticket.dto;

import jakarta.validation.constraints.Size;

public record CreateChatSessionRequest(@Size(max = 16) String locale) {}
