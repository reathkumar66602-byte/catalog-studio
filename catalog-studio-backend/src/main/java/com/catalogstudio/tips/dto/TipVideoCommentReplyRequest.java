package com.catalogstudio.tips.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipVideoCommentReplyRequest(
        @NotBlank @Size(max = 4000) String body
) {}
