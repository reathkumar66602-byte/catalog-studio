package com.catalogstudio.email.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.email.dto.MailIdentity;
import com.catalogstudio.email.service.MailIdentityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/mail")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin mail")
public class MailAdminController {

    private final CatalogStudioProperties properties;
    private final MailIdentityService mailIdentityService;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        CatalogStudioProperties.Mail mail = properties.mail();
        MailIdentity identity = mailIdentityService.current();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", mail != null && mail.enabled());
        out.put("provider", mail == null ? null : mail.provider());
        out.put("smtpProvider", mail != null && mail.smtpProvider());
        out.put("smtpReady", mail != null && mail.smtpReady());
        out.put("zeptomailTokenConfigured", mail != null && StringUtils.hasText(mail.zeptomailSendToken()));
        out.put("fromEmail", identity.fromEmail());
        out.put("fromName", identity.fromName());
        out.put("supportEmail", identity.supportEmail());
        out.put("otpEnabled", properties.otp() != null && properties.otp().enabled());
        boolean ready = mail != null && mail.enabled()
                && (mail.smtpProvider() ? mail.smtpReady() : StringUtils.hasText(mail.zeptomailSendToken()));
        out.put("readyForOutbound", ready);
        out.put("hint", ready
                ? "Outbound mail looks configured. If register OTP still fails, check server logs for ZeptoMail/SMTP errors and spam folders."
                : "Mail is not ready. Set MAIL_ENABLED=true and either Zoho SMTP credentials or ZEPTOMAIL_SEND_TOKEN.");
        return ApiResponse.ok(out);
    }
}
