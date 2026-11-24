package com.catalogstudio.internal.dto;

import java.util.Map;

public record InternalEntitlementResponse(
        String plan,
        boolean accessEntitled,
        Map<String, Object> features,
        boolean staff
) {}
