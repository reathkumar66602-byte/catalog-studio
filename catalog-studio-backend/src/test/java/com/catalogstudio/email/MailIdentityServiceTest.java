package com.catalogstudio.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.email.dto.MailIdentity;
import com.catalogstudio.email.service.MailIdentityService;
import com.catalogstudio.site.entity.SiteSettings;
import com.catalogstudio.site.repository.SiteSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MailIdentityServiceTest {

    @Mock SiteSettingsRepository settingsRepository;
    @Mock CatalogStudioProperties properties;
    @InjectMocks MailIdentityService identityService;

    @Test
    void usesDatabaseSupportAndFromAddresses() {
        when(properties.mail()).thenReturn(new CatalogStudioProperties.Mail(
                true, "zeptomail", "smtp.zoho.in", 587, "", "", "fallback@example.com", "Env Name", true,
                "https://api.zeptomail.in/v1.1/email", "token", ""));
        when(settingsRepository.findBySiteKey("default")).thenReturn(Optional.of(SiteSettings.builder()
                .supportEmail("support@catalogstudio.in")
                .mailFromEmail("support@catalogstudio.in")
                .mailFromName("Catalog Studio Support")
                .siteName("Catalog Studio")
                .build()));

        MailIdentity identity = identityService.current();
        assertThat(identity.supportEmail()).isEqualTo("support@catalogstudio.in");
        assertThat(identity.fromEmail()).isEqualTo("support@catalogstudio.in");
        assertThat(identity.fromName()).isEqualTo("Catalog Studio Support");
    }
}
