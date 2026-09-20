package com.catalogstudio.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        String email,
        String username,
        String login,
        @NotBlank String password,
        boolean rememberDevice,
        String deviceName,
        String captchaToken
) {
    public String identifier() {
        if (hasText(login)) {
            return login.trim();
        }
        if (hasText(email)) {
            return email.trim();
        }
        if (hasText(username)) {
            return username.trim();
        }
        return "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
