package com.catalogstudio.email.service;

import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.email.dto.OutboundMail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class ZeptoMailTransport implements MailTransport {

    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public ZeptoMailTransport(CatalogStudioProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    ZeptoMailTransport(CatalogStudioProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    @Override
    public boolean send(OutboundMail mail) {
        String token = properties.mail().zeptomailSendToken();
        String apiUrl = properties.mail().zeptomailApiUrl();
        if (!StringUtils.hasText(token)) {
            log.warn("ZEPTOMAIL_SEND_TOKEN is empty; skip send to {}", mail.to());
            return false;
        }
        if (!StringUtils.hasText(mail.fromEmail()) || !StringUtils.hasText(mail.to())) {
            log.warn("ZeptoMail skipped: from or to is blank");
            return false;
        }
        try {
            String body = objectMapper.writeValueAsString(payload(mail));
            String response = restClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", authorizationHeader(token))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("ZeptoMail accepted send to {} ref={} body={}", mail.to(), mail.clientReference(), abbreviate(response));
            return true;
        } catch (Exception ex) {
            log.error("ZeptoMail send failed to {}: {}", mail.to(), ex.getMessage());
            return false;
        }
    }

    static String authorizationHeader(String token) {
        String value = token.trim();
        if (value.toLowerCase().startsWith("zoho-enczapikey")) {
            return value;
        }
        return "Zoho-enczapikey " + value;
    }

    static Map<String, Object> payload(OutboundMail mail) {
        Map<String, Object> from = new LinkedHashMap<>();
        from.put("address", mail.fromEmail());
        if (StringUtils.hasText(mail.fromName())) {
            from.put("name", mail.fromName());
        }
        Map<String, Object> toAddress = new LinkedHashMap<>();
        toAddress.put("address", mail.to());
        Map<String, Object> toWrapper = new LinkedHashMap<>();
        toWrapper.put("email_address", toAddress);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from);
        payload.put("to", List.of(toWrapper));
        payload.put("subject", mail.subject());
        if (StringUtils.hasText(mail.htmlBody())) {
            payload.put("htmlbody", mail.htmlBody());
        }
        if (StringUtils.hasText(mail.textBody())) {
            payload.put("textbody", mail.textBody());
        }
        if (StringUtils.hasText(mail.replyTo())) {
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("address", mail.replyTo());
            payload.put("reply_to", List.of(reply));
        }
        if (StringUtils.hasText(mail.clientReference())) {
            payload.put("client_reference", mail.clientReference());
        }
        payload.put("track_clicks", false);
        payload.put("track_opens", false);
        return payload;
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 180 ? value : value.substring(0, 180);
    }

    static boolean looksSuccessful(String body, ObjectMapper mapper) {
        if (!StringUtils.hasText(body)) {
            return true;
        }
        try {
            JsonNode root = mapper.readTree(body);
            return !root.has("error");
        } catch (Exception ex) {
            return true;
        }
    }

    @SuppressWarnings("unused")
    private static List<Object> listOf(Object value) {
        List<Object> list = new ArrayList<>();
        list.add(value);
        return list;
    }
}
