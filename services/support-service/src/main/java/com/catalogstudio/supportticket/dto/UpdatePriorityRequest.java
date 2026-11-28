package com.catalogstudio.supportticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePriorityRequest(@NotBlank @Size(max = 16) String priority) {}
