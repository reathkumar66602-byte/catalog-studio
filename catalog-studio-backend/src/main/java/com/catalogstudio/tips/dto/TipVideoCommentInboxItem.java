package com.catalogstudio.tips.dto;

import java.time.Instant;
import java.util.UUID;

public record TipVideoCommentInboxItem(
        UUID id,
        UUID tipVideoId,
        String videoTitle,
        String userName,
        String body,
        Instant createdAt
) {}
