package com.catalogstudio.ai.provider;

import com.catalogstudio.ai.AIProductAnalysisService;
import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisResponse;
import com.catalogstudio.ai.AIProvider;
import com.catalogstudio.ai.VisionModelClient;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GeminiVisionProvider implements VisionModelClient {

    private final CatalogStudioProperties properties;
    private final MockVisionProvider mockVisionProvider;

    public GeminiVisionProvider(CatalogStudioProperties properties, MockVisionProvider mockVisionProvider) {
        this.properties = properties;
        this.mockVisionProvider = mockVisionProvider;
    }

    @Override
    public AIProvider provider() {
        return AIProvider.GEMINI;
    }

    @Override
    public ProductAnalysisResponse analyze(AIProductAnalysisService.ProductAnalysisRequest request, String prompt) {
        if (!StringUtils.hasText(properties.ai().apiKey())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_API_KEY is not configured for Gemini");
        }
        // Provider-ready seam: Gemini HTTP integration can replace the mock fallback.
        return mockVisionProvider.analyze(request, prompt);
    }
}
