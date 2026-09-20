package com.catalogstudio.ai.provider;

import com.catalogstudio.ai.AIProductAnalysisService;
import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisResponse;
import com.catalogstudio.ai.AIProvider;
import com.catalogstudio.ai.VisionModelClient;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MockVisionProvider implements VisionModelClient {

    @Override
    public AIProvider provider() {
        return AIProvider.MOCK;
    }

    @Override
    public ProductAnalysisResponse analyze(AIProductAnalysisService.ProductAnalysisRequest request, String prompt) {
        String hintType = request.productTypeHint() == null || request.productTypeHint().isBlank()
                ? "Shirt"
                : request.productTypeHint();
        return new ProductAnalysisResponse(
                hintType,
                "Clothing",
                "Men Shirts",
                "Men",
                "Adult",
                "Navy Blue",
                List.of("White"),
                0.94,
                "Checked",
                0.91,
                null,
                0.42,
                "Full Sleeve",
                null,
                "Shirt Collar",
                "Regular Fit",
                "Casual",
                "Casual",
                "Men's navy blue checked casual shirt with a regular fit and full sleeves.",
                List.of(
                        "Men's Navy Blue Checked Casual Shirt",
                        "Men's Cotton Style Navy Checked Full Sleeve Shirt",
                        "Men's Regular Fit Navy Blue Checked Shirt",
                        "Stylish Men's Navy Blue Check Shirt"
                ),
                List.of(
                        "Men's navy blue checked casual shirt with a regular fit and full sleeves. Key highlights: everyday wear, lightweight look, easy pairing with jeans or trousers.",
                        "Casual navy checked shirt for men with a regular fit. Key highlights: full sleeves, shirt collar, suitable for daily and office wear."
                ),
                List.of("men shirt", "navy blue shirt", "checked shirt", "casual shirt"),
                0.89,
                List.of("material"),
                "MOCK",
                "mock-vision-v1"
        );
    }
}
