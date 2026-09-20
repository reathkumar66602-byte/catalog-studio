package com.catalogstudio.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 80)
        @Pattern(regexp = "^[A-Za-z0-9._-]{3,80}$", message = "Username may contain letters, numbers, dots, underscores, and hyphens")
        String username,
        @Size(max = 120) String fullName,
        @Size(max = 200) String businessName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 255) String confirmEmail,
        @Pattern(regexp = "^$|^[0-9+]{8,20}$", message = "Enter a valid mobile number") String mobile,
        @Size(max = 40) String referralCode,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank String confirmPassword,
        boolean termsAccepted
) {}
