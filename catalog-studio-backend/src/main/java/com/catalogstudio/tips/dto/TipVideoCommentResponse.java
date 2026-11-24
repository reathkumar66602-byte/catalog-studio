package com.catalogstudio.tips.dto;

import java.time.Instant;
import java.util.UUID;

public record TipVideoCommentResponse(
        UUID id,
        UUID tipVideoId,
        UUID parentId,
        String body,
        String userName,
        boolean staffReply,
        Instant createdAt
) {}
