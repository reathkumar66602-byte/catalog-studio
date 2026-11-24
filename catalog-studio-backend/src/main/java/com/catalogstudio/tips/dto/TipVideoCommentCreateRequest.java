package com.catalogstudio.tips.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TipVideoCommentCreateRequest(
        @NotBlank @Size(max = 4000) String body,
        UUID parentId
) {}
