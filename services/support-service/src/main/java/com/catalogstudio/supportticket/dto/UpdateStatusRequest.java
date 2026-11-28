package com.catalogstudio.supportticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStatusRequest(@NotBlank @Size(max = 32) String status) {}
