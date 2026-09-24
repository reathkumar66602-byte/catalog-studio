package com.catalogstudio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.catalogstudio.ai.provider.AnalysisJsonParser;
import com.catalogstudio.common.exception.ApiException;
import org.junit.jupiter.api.Test;

class AnalysisJsonParserTest {

    private final AnalysisJsonParser parser = new AnalysisJsonParser(new ObjectMapper());

    @Test
    void parsesStructuredProductJson() {
        String json = "{"
                + "\"productType\": \"Shirt\","
                + "\"category\": \"Clothing\","
                + "\"primaryColor\": \"Navy Blue\","
                + "\"material\": null,"
                + "\"materialConfidence\": 0.42,"
                + "\"overallConfidence\": 0.89,"
                + "\"uncertainFields\": [\"material\"],"
                + "\"suggestedTitles\": [\"Men's Navy Blue Checked Casual Shirt\"],"
                + "\"lengthMeters\": 1.5"
                + "}";
        var result = parser.parse(json, "MOCK", "mock-vision-v1");
        assertThat(result.productType()).isEqualTo("Shirt");
        assertThat(result.lengthMeters()).isEqualTo("1.5");
        assertThat(result.material()).isNull();
        assertThat(result.uncertainFields()).contains("material");
        assertThat(result.overallConfidence()).isEqualTo(0.89);
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> parser.parse("not-json", "MOCK", "mock"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("invalid JSON");
    }
}
