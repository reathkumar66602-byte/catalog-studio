package com.catalogstudio.site.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SitePublicResponse(
        Branding branding,
        Support support,
        EnquiryForm enquiry,
        ClientPublic client,
        List<PromoPublic> promoCodes
) {
    public record Branding(
            String siteName,
            String tagline,
            String heroTitle,
            String heroSubtitle,
            String logoUrl,
            String primaryColor,
            String accentColor,
            String heroBackground,
            String footerText
    ) {}

    public record Support(String email, String phone) {}

    public record EnquiryForm(boolean enabled, String intro, String successMessage) {}

    public record ClientPublic(
            UUID id,
            String storeName,
            String slug,
            String ownerName,
            String email,
            String phone,
            String address,
            String websiteUrl,
            String logoUrl,
            String tagline,
            String about,
            Map<String, Object> branding
    ) {}

    public record PromoPublic(
            String code,
            String headline,
            String description,
            String discountType,
            BigDecimal discountValue,
            int trialDays,
            LocalDate validUntil
    ) {}

    public record EnquiryAck(UUID id, String message) {}

    public record SiteSettingsResponse(
            String siteName,
            String tagline,
            String heroTitle,
            String heroSubtitle,
            String logoUrl,
            String primaryColor,
            String accentColor,
            String heroBackground,
            String footerText,
            String supportEmail,
            String mailFromEmail,
            String mailFromName,
            String supportPhone,
            boolean enquiryEnabled,
            String enquiryIntro,
            String enquirySuccessMessage
    ) {}

    public record ClientAdminResponse(
            UUID id,
            String storeName,
            String slug,
            String ownerName,
            String email,
            String phone,
            String address,
            String websiteUrl,
            String logoUrl,
            String tagline,
            String about,
            Map<String, Object> branding,
            boolean featured,
            String status
    ) {}

    public record PromoAdminResponse(
            UUID id,
            UUID clientId,
            String storeName,
            String code,
            String headline,
            String description,
            String discountType,
            BigDecimal discountValue,
            int trialDays,
            LocalDate validFrom,
            LocalDate validUntil,
            String status
    ) {}

    public record EnquiryAdminResponse(
            UUID id,
            String name,
            String email,
            String phone,
            String storeName,
            String subject,
            String message,
            String status,
            Instant createdAt
    ) {}

    public record AdminSiteBundle(
            SiteSettingsResponse settings,
            List<ClientAdminResponse> clients,
            List<PromoAdminResponse> promoCodes,
            List<EnquiryAdminResponse> enquiries
    ) {}
}
