package com.catalogstudio.supportticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeArticleRequest(
        @NotBlank @Size(max = 180) String slug,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2000) String summary,
        @NotBlank String body,
        @Size(max = 16) String locale,
        @Size(max = 64) String category,
        @Size(max = 500) String tags,
        Boolean active,
        Boolean verified,
        Integer sortOrder
) {}
