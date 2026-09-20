package com.catalogstudio.site.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "site_settings")
public class SiteSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_key", nullable = false, unique = true, length = 40)
    private String siteKey;

    @Column(name = "site_name", nullable = false, length = 120)
    private String siteName;

    @Column(length = 255)
    private String tagline;

    @Column(name = "hero_title", nullable = false, columnDefinition = "text")
    private String heroTitle;

    @Column(name = "hero_subtitle", columnDefinition = "text")
    private String heroSubtitle;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "primary_color", length = 20)
    private String primaryColor;

    @Column(name = "accent_color", length = 20)
    private String accentColor;

    @Column(name = "hero_background", length = 20)
    private String heroBackground;

    @Column(name = "footer_text", columnDefinition = "text")
    private String footerText;

    @Column(name = "support_email", nullable = false)
    private String supportEmail;

    @Column(name = "mail_from_email", length = 255)
    private String mailFromEmail;

    @Column(name = "mail_from_name", length = 120)
    private String mailFromName;

    @Column(name = "support_phone", length = 40)
    private String supportPhone;

    @Builder.Default
    @Column(name = "enquiry_enabled", nullable = false)
    private boolean enquiryEnabled = true;

    @Column(name = "enquiry_intro", columnDefinition = "text")
    private String enquiryIntro;

    @Column(name = "enquiry_success_message", columnDefinition = "text")
    private String enquirySuccessMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (siteKey == null) {
            siteKey = "default";
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
