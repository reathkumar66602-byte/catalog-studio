package com.catalogstudio.internal.dto;

public record InternalUserResponse(
        Long id,
        String name,
        String email,
        String mobile,
        String role,
        boolean active
) {}
