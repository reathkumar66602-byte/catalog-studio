package com.catalogstudio.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnquiryStatusRequest(@NotBlank @Size(max = 32) String status) {}
