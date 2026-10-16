package com.catalogstudio.marketplace.meesho;

public record MeeshoLabelResult(
        String externalOrderId,
        String awb,
        String labelUrl,
        String status,
        String message
) {}
