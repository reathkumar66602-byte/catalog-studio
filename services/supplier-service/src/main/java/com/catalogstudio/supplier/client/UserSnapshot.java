package com.catalogstudio.supplier.client;

public record UserSnapshot(
        Long id,
        String name,
        String email,
        String mobile,
        String role,
        boolean active
) {}
