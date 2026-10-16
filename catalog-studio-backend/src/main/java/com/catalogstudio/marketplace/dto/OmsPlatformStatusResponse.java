package com.catalogstudio.marketplace.dto;

import java.util.List;

public record OmsPlatformStatusResponse(
        String source,
        List<OmsChannelStatusResponse> channels,
        String guidance
) {}
