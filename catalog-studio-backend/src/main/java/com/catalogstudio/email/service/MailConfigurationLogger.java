package com.catalogstudio.email.service;

import com.catalogstudio.config.CatalogStudioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class MailConfigurationLogger implements ApplicationRunner {

    private final CatalogStudioProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        CatalogStudioProperties.Mail mail = properties.mail();
        if (!mail.enabled()) {
            log.warn("Mail is disabled. Registration OTP and enquiry emails will not be sent.");
            return;
        }
        if (mail.smtpProvider()) {
            if (!mail.smtpReady()) {
                log.error("MAIL_PROVIDER=zoho but ZOHO_MAIL_USERNAME or ZOHO_MAIL_PASSWORD is missing. OTP and enquiry emails will not send.");
                return;
            }
            if (mail.gmailMailbox() && mail.host() != null && mail.host().toLowerCase().contains("zoho")) {
                log.warn(
                        "Mailbox is Gmail but host was {}. Switching to smtp.gmail.com so Gmail recipients are not silently dropped (DMARC).",
                        mail.host());
            }
            log.info(
                    "SMTP ready host={} port={} user={} from={}",
                    mail.resolvedHost(),
                    mail.port(),
                    mail.maskedUsername(),
                    mail.smtpFromAddress());
            return;
        }
        if ("zeptomail".equalsIgnoreCase(mail.provider())) {
            if (!StringUtils.hasText(mail.zeptomailSendToken())) {
                log.error("MAIL_PROVIDER=zeptomail but ZEPTOMAIL_SEND_TOKEN is empty. OTP and enquiry emails will not send.");
                return;
            }
            log.info("ZeptoMail ready api={}", mail.zeptomailApiUrl());
            return;
        }
        log.warn("Unknown MAIL_PROVIDER '{}'. OTP and enquiry emails will not send.", mail.provider());
    }
}
