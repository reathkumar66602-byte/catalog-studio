package com.catalogstudio.supplier.client;

import java.util.Map;

public record EntitlementSnapshot(
        String plan,
        boolean accessEntitled,
        Map<String, Object> features,
        boolean staff
) {}
