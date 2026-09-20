package com.catalogstudio.ai;

public enum AIProvider {
    OPENAI,
    GEMINI,
    MOCK;

    public static AIProvider from(String value) {
        if (value == null || value.isBlank()) {
            return MOCK;
        }
        try {
            return AIProvider.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MOCK;
        }
    }
}
