package com.catalogstudio.email.dto;

public record OutboundMail(
        String fromEmail,
        String fromName,
        String to,
        String replyTo,
        String subject,
        String htmlBody,
        String textBody,
        String clientReference
) {}
