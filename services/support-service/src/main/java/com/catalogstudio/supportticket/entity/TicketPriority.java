package com.catalogstudio.supportticket.entity;

public enum TicketPriority {
    LOW, MEDIUM, HIGH, URGENT;

    public static TicketPriority from(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEDIUM;
        }
        return TicketPriority.valueOf(raw.trim().toUpperCase());
    }
}
