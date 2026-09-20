package com.catalogstudio.ai;

public interface VisionModelClient {

    AIProvider provider();

    AIProductAnalysisService.ProductAnalysisResponse analyze(
            AIProductAnalysisService.ProductAnalysisRequest request,
            String prompt
    );
}
