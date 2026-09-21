package com.catalogstudio.ai;

import org.springframework.util.StringUtils;

public final class AiProviderResolver {

    private AiProviderResolver() {}

    public static AIProvider resolve(String provider, String apiKey) {
        AIProvider requested = AIProvider.from(provider);
        if (requested != AIProvider.MOCK) {
            return requested;
        }
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.startsWith("sk-")) {
            return AIProvider.OPENAI;
        }
        if (key.startsWith("AIza")) {
            return AIProvider.GEMINI;
        }
        return AIProvider.MOCK;
    }

    public static String geminiModel(String configured) {
        if (!StringUtils.hasText(configured)) {
            return "gemini-2.0-flash";
        }
        String model = configured.trim();
        String lower = model.toLowerCase();
        if (lower.startsWith("gpt-") || lower.contains("openai")) {
            return "gemini-2.0-flash";
        }
        return model;
    }
}
