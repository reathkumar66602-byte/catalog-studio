package com.catalogstudio.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.ai.provider.MockVisionProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockVisionProviderTest {

    private final MockVisionProvider provider = new MockVisionProvider();

    @Test
    void returnsUncertainMaterialInsteadOfInventingFabric() {
        var result = provider.analyze(new AIProductAnalysisService.ProductAnalysisRequest(
                List.of(), null, null, "Shirt"), "prompt");
        assertThat(result.material()).isNull();
        assertThat(result.uncertainFields()).contains("material");
        assertThat(result.suggestedTitles()).isNotEmpty();
        assertThat(result.provider()).isEqualTo("MOCK");
    }

    @Test
    void doesNotDefaultEveryProductToMensNavyShirt() {
        var result = provider.analyze(new AIProductAnalysisService.ProductAnalysisRequest(
                List.of(), "MEESHO", "Women Fashion / Shirt", "checked shirt"), "prompt");
        assertThat(result.gender()).isEqualTo("Women");
        assertThat(result.primaryColor()).isNotEqualTo("Navy Blue");
        assertThat(result.suggestedTitles()).allMatch(title -> !title.toLowerCase().matches(".*\\bmen\\b.*"));
        assertThat(result.productDescription()).containsIgnoringCase("mock");
    }
}
