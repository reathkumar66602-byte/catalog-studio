package com.catalogstudio.admin.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdminUserRow(
        UUID id,
        String name,
        String email,
        String mobile,
        String username,
        String role,
        String displayRole,
        String status,
        String plan,
        String planStatus,
        boolean accessEntitled,
        String subscriptionStartDate,
        String subscriptionEndDate,
        Instant createdAt,
        Map<String, Boolean> features
) {}
