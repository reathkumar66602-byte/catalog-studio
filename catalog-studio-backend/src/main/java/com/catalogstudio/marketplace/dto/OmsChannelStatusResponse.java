package com.catalogstudio.marketplace.dto;

public record OmsChannelStatusResponse(
        String channel,
        boolean enabled,
        boolean configured,
        boolean sandbox,
        String detailMasked,
        String note
) {}
