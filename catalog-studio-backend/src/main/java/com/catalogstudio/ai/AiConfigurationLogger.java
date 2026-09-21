package com.catalogstudio.ai;

import com.catalogstudio.config.CatalogStudioProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiConfigurationLogger implements ApplicationRunner {

    private final CatalogStudioProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        CatalogStudioProperties.Ai ai = properties.ai();
        AIProvider resolved = AiProviderResolver.resolve(ai.provider(), ai.apiKey());
        if (resolved == AIProvider.MOCK) {
            log.warn("AI provider is MOCK. Generate will not read the product photo. Set AI_PROVIDER=openai or gemini and AI_API_KEY.");
            return;
        }
        if (!StringUtils.hasText(ai.apiKey())) {
            log.error("AI_PROVIDER={} but AI_API_KEY is missing. Image analysis will fail.", resolved);
            return;
        }
        log.info("AI vision ready provider={} model={}", resolved, resolved == AIProvider.GEMINI
                ? AiProviderResolver.geminiModel(ai.model())
                : ai.model());
    }
}
