package com.catalogstudio.supportticket.ai;

import com.catalogstudio.support.config.SupportProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupportAiClientResolver {

    private final SupportProperties properties;
    private final MockSupportClient mockSupportClient;
    private final OpenAiSupportClient openAiSupportClient;
    private final GeminiSupportClient geminiSupportClient;

    public SupportAiClient.AiReply reply(
            String locale,
            List<SupportAiClient.ChatTurn> history,
            String userMessage,
            String kbContext) {
        String provider = properties.support().ai().providerKey();
        try {
            SupportAiClient client = switch (provider) {
                case "openai" -> openAiSupportClient;
                case "gemini", "google" -> geminiSupportClient;
                default -> mockSupportClient;
            };
            SupportAiClient.AiReply reply = client.reply(locale, history, userMessage, kbContext);
            if (reply == null || reply.content() == null || reply.content().isBlank()) {
                return SupportAiClient.AiReply.escalate(SupportAiPrompts.escalateMessage(locale));
            }
            if (!reply.confident()) {
                return new SupportAiClient.AiReply(reply.content(), false, true);
            }
            return reply;
        } catch (Exception ex) {
            log.warn("Support AI provider {} failed: {}", provider, ex.toString());
            return SupportAiClient.AiReply.escalate(SupportAiPrompts.escalateMessage(locale));
        }
    }
}
