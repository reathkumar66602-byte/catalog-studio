package com.catalogstudio.email.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.email.dto.OutboundMail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZeptoMailTransportTest {

    @Test
    void prefixesSendMailToken() {
        assertThat(ZeptoMailTransport.authorizationHeader("abc")).isEqualTo("Zoho-enczapikey abc");
        assertThat(ZeptoMailTransport.authorizationHeader("Zoho-enczapikey xyz"))
                .isEqualTo("Zoho-enczapikey xyz");
    }

    @Test
    void buildsIndiaApiPayloadWithReplyTo() {
        OutboundMail mail = new OutboundMail(
                "support@catalogstudio.in",
                "Catalog Studio",
                "seller@example.com",
                "support@catalogstudio.in",
                "Your Catalog Studio login code is 123456",
                "<p>123456</p>",
                "123456",
                "otp-login");
        Map<String, Object> payload = ZeptoMailTransport.payload(mail);
        assertThat(payload.get("from")).isEqualTo(Map.of("address", "support@catalogstudio.in", "name", "Catalog Studio"));
        assertThat(payload.get("subject")).isEqualTo("Your Catalog Studio login code is 123456");
        assertThat(payload.get("htmlbody")).isEqualTo("<p>123456</p>");
        assertThat(payload.get("client_reference")).isEqualTo("otp-login");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reply = (List<Map<String, Object>>) payload.get("reply_to");
        assertThat(reply.get(0).get("address")).isEqualTo("support@catalogstudio.in");
    }
}
