package com.catalogstudio.site.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SiteSettingsRequest(
        @NotBlank @Size(max = 120) String siteName,
        @Size(max = 255) String tagline,
        @NotBlank String heroTitle,
        String heroSubtitle,
        @Size(max = 500) String logoUrl,
        @Size(max = 20) String primaryColor,
        @Size(max = 20) String accentColor,
        @Size(max = 20) String heroBackground,
        String footerText,
        @NotBlank @Email @Size(max = 255) String supportEmail,
        @Size(max = 255) String mailFromEmail,
        @Size(max = 120) String mailFromName,
        @Size(max = 40) String supportPhone,
        boolean enquiryEnabled,
        String enquiryIntro,
        String enquirySuccessMessage
) {}
