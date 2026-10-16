package com.catalogstudio.tips.dto;

public record TipVideoSettingsResponse(
        int maxCount,
        long maxTotalBytes,
        long usedBytes,
        long videoCount
) {}
