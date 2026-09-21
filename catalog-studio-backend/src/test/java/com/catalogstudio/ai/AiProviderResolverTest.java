package com.catalogstudio.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiProviderResolverTest {

    @Test
    void keepsExplicitProvider() {
        assertThat(AiProviderResolver.resolve("openai", "")).isEqualTo(AIProvider.OPENAI);
        assertThat(AiProviderResolver.resolve("gemini", "sk-test")).isEqualTo(AIProvider.GEMINI);
    }

    @Test
    void upgradesMockWhenRealKeyIsPresent() {
        assertThat(AiProviderResolver.resolve("mock", "sk-live-key")).isEqualTo(AIProvider.OPENAI);
        assertThat(AiProviderResolver.resolve("mock", "AIzaSyDummy")).isEqualTo(AIProvider.GEMINI);
    }

    @Test
    void keepsMockForTestOrMissingKeys() {
        assertThat(AiProviderResolver.resolve("mock", "test")).isEqualTo(AIProvider.MOCK);
        assertThat(AiProviderResolver.resolve("mock", "")).isEqualTo(AIProvider.MOCK);
        assertThat(AiProviderResolver.resolve(null, null)).isEqualTo(AIProvider.MOCK);
    }

    @Test
    void remapsOpenAiModelNamesForGemini() {
        assertThat(AiProviderResolver.geminiModel("gpt-4o-mini")).isEqualTo("gemini-2.0-flash");
        assertThat(AiProviderResolver.geminiModel("gemini-1.5-flash")).isEqualTo("gemini-1.5-flash");
    }
}
