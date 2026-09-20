package com.catalogstudio.email.service;

import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.email.dto.MailIdentity;
import com.catalogstudio.email.dto.OutboundMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailDispatchService {

    private final CatalogStudioProperties properties;
    private final MailIdentityService identityService;
    private final ZeptoMailTransport zeptoMailTransport;
    private final ZohoSmtpTransport zohoSmtpTransport;

    public boolean sendHtml(String to, String subject, String htmlBody, String textBody) {
        MailIdentity identity = identityService.current();
        return send(new OutboundMail(
                identity.fromEmail(),
                identity.fromName(),
                to,
                identity.supportEmail(),
                subject,
                htmlBody,
                textBody,
                "catalog-studio"));
    }

    public boolean sendHtml(String to, String replyTo, String subject, String htmlBody, String textBody, String reference) {
        MailIdentity identity = identityService.current();
        return send(new OutboundMail(
                identity.fromEmail(),
                identity.fromName(),
                to,
                replyTo,
                subject,
                htmlBody,
                textBody,
                reference));
    }

    public boolean sendToSupport(String replyTo, String subject, String htmlBody, String textBody) {
        MailIdentity identity = identityService.current();
        return send(new OutboundMail(
                identity.fromEmail(),
                identity.fromName(),
                identity.supportEmail(),
                replyTo,
                subject,
                htmlBody,
                textBody,
                "enquiry"));
    }

    boolean send(OutboundMail mail) {
        if (!properties.mail().enabled()) {
            log.info("Mail disabled; skip send to {}", mail.to());
            return false;
        }
        if (properties.mail().smtpProvider()) {
            return zohoSmtpTransport.send(mail);
        }
        String provider = properties.mail().provider() == null ? "zoho" : properties.mail().provider().trim();
        if ("zeptomail".equalsIgnoreCase(provider)) {
            return zeptoMailTransport.send(mail);
        }
        log.warn("Unknown MAIL_PROVIDER '{}'; skip send", provider);
        return false;
    }

    public MailIdentity identity() {
        return identityService.current();
    }

    static boolean hasAddress(String value) {
        return StringUtils.hasText(value);
    }
}
