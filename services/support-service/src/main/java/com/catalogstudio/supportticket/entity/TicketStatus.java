package com.catalogstudio.supportticket.entity;

public enum TicketStatus {
    OPEN, ASSIGNED, IN_PROGRESS, WAITING_FOR_CUSTOMER, RESOLVED, CLOSED, REOPENED;

    public boolean isTerminal() {
        return this == RESOLVED || this == CLOSED;
    }

    public static TicketStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        return TicketStatus.valueOf(raw.trim().toUpperCase());
    }
}
