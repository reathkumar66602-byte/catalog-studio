package com.catalogstudio.ai;

import com.catalogstudio.config.CatalogStudioProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultAIProductAnalysisService implements AIProductAnalysisService {

    private final CatalogStudioProperties properties;
    private final ResourceLoader resourceLoader;
    private final List<VisionModelClient> clients;

    @Override
    public ProductAnalysisResponse analyze(ProductAnalysisRequest request) {
        AIProvider provider = AIProvider.from(properties.ai().provider());
        VisionModelClient client = clients.stream()
                .filter(c -> c.provider() == provider)
                .findFirst()
                .orElseGet(() -> clients.stream()
                        .filter(c -> c.provider() == AIProvider.MOCK)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No AI provider registered")));
        return client.analyze(request, loadPrompt(request));
    }

    private String loadPrompt(ProductAnalysisRequest request) {
        try {
            Resource resource = resourceLoader.getResource(properties.ai().promptPath());
            String template = resource.getContentAsString(StandardCharsets.UTF_8);
            return template
                    .replace("{{marketplace}}", nullToEmpty(request.marketplace()))
                    .replace("{{categoryHint}}", nullToEmpty(request.categoryHint()))
                    .replace("{{productTypeHint}}", nullToEmpty(request.productTypeHint()));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load AI prompt template", e);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
