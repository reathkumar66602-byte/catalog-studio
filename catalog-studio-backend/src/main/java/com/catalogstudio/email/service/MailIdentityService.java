package com.catalogstudio.email.service;

import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.email.dto.MailIdentity;
import com.catalogstudio.site.entity.SiteSettings;
import com.catalogstudio.site.repository.SiteSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MailIdentityService {

    static final String DEFAULT_FROM = "support@catalogstudio.in";
    static final String DEFAULT_NAME = "Catalog Studio";

    private final SiteSettingsRepository settingsRepository;
    private final CatalogStudioProperties properties;

    @Transactional(readOnly = true)
    public MailIdentity current() {
        SiteSettings settings = settingsRepository.findBySiteKey("default").orElse(null);
        CatalogStudioProperties.Mail mail = properties.mail();
        String support = first(
                mail.supportInbox(),
                mail.smtpProvider() ? mail.smtpFromAddress() : null,
                settings == null ? null : settings.getSupportEmail(),
                mail.fromAddress(),
                DEFAULT_FROM);
        String fromEmail;
        if (mail.smtpProvider() && StringUtils.hasText(mail.smtpFromAddress())) {
            fromEmail = mail.smtpFromAddress();
        } else {
            fromEmail = first(
                    settings == null ? null : settings.getMailFromEmail(),
                    support,
                    mail.fromAddress(),
                    DEFAULT_FROM);
        }
        String fromName = firstNonBlank(
                settings == null ? null : settings.getMailFromName(),
                settings == null ? null : settings.getSiteName(),
                mail.fromName(),
                DEFAULT_NAME);
        return new MailIdentity(fromEmail, fromName, support);
    }

    private static String first(String... values) {
        return firstNonBlank(values) == null ? DEFAULT_FROM : firstNonBlank(values);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
