package com.catalogstudio.supportticket.ai;

import java.util.List;

public interface SupportAiClient {

    AiReply reply(String locale, List<ChatTurn> history, String userMessage, String kbContext);

    record ChatTurn(String role, String content) {}

    record AiReply(String content, boolean confident, boolean suggestTicket) {
        public static AiReply escalate(String content) {
            return new AiReply(content, false, true);
        }
    }
}
