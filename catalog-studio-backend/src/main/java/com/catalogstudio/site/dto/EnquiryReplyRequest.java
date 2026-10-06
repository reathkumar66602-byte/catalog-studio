package com.catalogstudio.site.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EnquiryReplyRequest(
        @NotBlank @Size(max = 8000) String replyBody
) {}
