package com.catalogstudio.email.service;

import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.email.dto.OutboundMail;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZohoSmtpTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final CatalogStudioProperties properties;

    @Override
    public boolean send(OutboundMail mail) {
        if (!properties.mail().smtpReady()) {
            log.error("Zoho SMTP is not configured; skip send to {}", mail.to());
            return false;
        }
        if (!StringUtils.hasText(mail.to())) {
            log.warn("Zoho SMTP skipped: recipient is blank");
            return false;
        }
        String fromEmail = firstNonBlank(properties.mail().smtpFromAddress(), mail.fromEmail());
        String fromName = firstNonBlank(mail.fromName(), properties.mail().fromName(), "Catalog Studio");
        if (!StringUtils.hasText(fromEmail)) {
            log.error("Zoho SMTP skipped: authenticated mailbox (ZOHO_MAIL_USERNAME) is blank");
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(mail.to().trim());
            message.setHeader("X-Mailer", "Catalog Studio");
            message.setHeader("Auto-Submitted", "auto-generated");
            if (StringUtils.hasText(mail.replyTo())) {
                helper.setReplyTo(mail.replyTo().trim());
            }
            helper.setSubject(mail.subject() == null ? "" : mail.subject());
            String text = mail.textBody() == null ? "" : mail.textBody();
            String html = mail.htmlBody() == null ? "" : mail.htmlBody();
            if (StringUtils.hasText(html)) {
                helper.setText(text, html);
            } else {
                helper.setText(text, false);
            }
            mailSender.send(message);
            log.info(
                    "Sent SMTP email via {} to {} from {} ref={}",
                    properties.mail().resolvedHost(),
                    mail.to(),
                    fromEmail,
                    mail.clientReference());
            return true;
        } catch (MailAuthenticationException | AuthenticationFailedException ex) {
            if (properties.mail().gmailMailbox()) {
                log.error(
                        "Gmail SMTP authentication failed for {}. Create a Google App Password at https://myaccount.google.com/apppasswords and set ZOHO_MAIL_PASSWORD to that 16-character value.",
                        properties.mail().maskedUsername());
            } else {
                log.error(
                        "SMTP authentication failed for {}. Use the provider mailbox address and an app password if 2FA is on.",
                        properties.mail().maskedUsername());
            }
            return false;
        } catch (Exception ex) {
            log.error("Failed to send SMTP email to {}: {}", mail.to(), rootMessage(ex));
            return false;
        }
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

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        String message = ex.getMessage();
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (StringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
            }
        }
        return message;
    }
}
