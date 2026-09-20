package com.catalogstudio.site.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnquiryRequest(
        @NotBlank(message = "Enter your name")
        @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
        @Pattern(
                regexp = "^[\\p{L}][\\p{L} .'\\-]{1,119}$",
                message = "Name may contain letters, spaces, apostrophes, and hyphens")
        String name,

        @NotBlank(message = "Enter your email address")
        @Email(message = "Enter a valid email address")
        @Size(max = 255, message = "Email must be 255 characters or fewer")
        String email,

        @Pattern(
                regexp = "^$|^(\\+91[\\s-]?|91[\\s-]?|0)?[6-9]\\d{9}$",
                message = "Enter a valid 10-digit Indian mobile number")
        @Size(max = 40, message = "Phone must be 40 characters or fewer")
        String phone,

        @Size(max = 200, message = "Store name must be 200 characters or fewer")
        String storeName,

        @NotBlank(message = "Enter a subject")
        @Size(min = 5, max = 200, message = "Subject must be between 5 and 200 characters")
        String subject,

        @NotBlank(message = "Enter your message")
        @Size(min = 20, max = 4000, message = "Message must be between 20 and 4000 characters")
        String message
) {}
