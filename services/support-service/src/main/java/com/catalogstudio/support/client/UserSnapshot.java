package com.catalogstudio.support.client;

public record UserSnapshot(
        Long id,
        String name,
        String email,
        String mobile,
        String role,
        boolean active,
        String preferredLocale
) {}
