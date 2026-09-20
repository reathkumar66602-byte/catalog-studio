package com.catalogstudio.email.service;

import com.catalogstudio.email.entity.EmailTemplate;
import com.catalogstudio.email.repository.EmailTemplateRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TemplatedEmailService {

    private final EmailTemplateRepository templateRepository;
    private final MailDispatchService mailDispatchService;

    @Transactional(readOnly = true)
    public boolean send(String slug, String to, Map<String, String> variables) {
        return send(slug, to, null, variables);
    }

    @Transactional(readOnly = true)
    public boolean send(String slug, String to, String replyTo, Map<String, String> variables) {
        Map<String, String> vars = new HashMap<>();
        if (variables != null) {
            vars.putAll(variables);
        }
        vars.putIfAbsent("appName", "Catalog Studio");
        EmailTemplate template = templateRepository.findBySlugIgnoreCase(slug).orElse(null);
        String subject;
        String html;
        String text;
        if (template == null || !template.isEnabled()) {
            String otp = vars.getOrDefault("otp", "");
            if (StringUtils.hasText(otp)) {
                subject = "Your Catalog Studio verification code is " + otp;
                text = "Your verification code is " + otp + ". It expires in "
                        + vars.getOrDefault("expiresMinutes", "10") + " minutes.";
                html = "<p>" + text + "</p>";
            } else if ("enquiry-ack".equalsIgnoreCase(slug)) {
                subject = "We received your Catalog Studio enquiry";
                text = "Hi " + vars.getOrDefault("name", "") + ", we received your enquiry"
                        + (StringUtils.hasText(vars.get("subject")) ? " about " + vars.get("subject") : "")
                        + ". Our team will reply from support@catalogstudio.in.";
                html = "<p>" + text + "</p>";
            } else if ("enquiry-received".equalsIgnoreCase(slug)) {
                subject = "New Catalog Studio enquiry from " + vars.getOrDefault("name", "");
                text = "New enquiry from " + vars.getOrDefault("name", "") + " (" + vars.getOrDefault("email", "") + "). "
                        + vars.getOrDefault("message", "");
                html = "<p>" + text + "</p>";
            } else {
                subject = vars.getOrDefault("subject", "Catalog Studio message");
                text = vars.getOrDefault("message", "");
                html = "<p>" + text + "</p>";
            }
        } else {
            subject = EmailTemplateRenderer.render(template.getSubject(), vars);
            html = EmailTemplateRenderer.render(template.getHtmlBody(), vars);
            text = EmailTemplateRenderer.render(template.getTextBody(), vars);
        }
        if (StringUtils.hasText(replyTo)) {
            return mailDispatchService.sendHtml(to, replyTo, subject, html, text, slug);
        }
        return mailDispatchService.sendHtml(to, subject, html, text);
    }
}
