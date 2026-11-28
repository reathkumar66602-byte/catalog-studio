package com.catalogstudio.supportticket.dto;

public record AdminStatsResponse(
        long totalCount,
        long openCount,
        long assignedCount,
        long inProgressCount,
        long waitingForCustomerCount,
        long resolvedCount,
        long closedCount,
        long unassignedCount,
        long slaBreachedCount
) {}
