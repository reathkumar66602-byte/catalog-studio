package com.catalogstudio.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.ai.provider.MockVisionProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class MockVisionProviderTest {

    @Test
    void returnsUncertainMaterialInsteadOfInventingFabric() {
        MockVisionProvider provider = new MockVisionProvider();
        var result = provider.analyze(new AIProductAnalysisService.ProductAnalysisRequest(
                List.of(), null, null, "Shirt"), "prompt");
        assertThat(result.material()).isNull();
        assertThat(result.uncertainFields()).contains("material");
        assertThat(result.suggestedTitles()).hasSize(4);
        assertThat(result.provider()).isEqualTo("MOCK");
    }
}
