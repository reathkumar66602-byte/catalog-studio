package com.catalogstudio.shoot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShootResponse(
        UUID id,
        String mode,
        String modelAge,
        boolean marketplace,
        boolean trialLimited,
        String status,
        String errorMessage,
        List<ShootImageView> images,
        Instant createdAt
) {
    public record ShootImageView(
            UUID id,
            String kind,
            String url,
            boolean downloadable,
            int sortOrder
    ) {}
}
