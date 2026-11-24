package com.catalogstudio.wholesale;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisResponse;
import com.catalogstudio.wholesale.service.WholesaleProductAutofillService;
import java.util.List;
import org.junit.jupiter.api.Test;

class WholesaleProductAutofillServiceTest {

    @Test
    void composeDetailsIncludesKnownAttributes() {
        ProductAnalysisResponse ai = new ProductAnalysisResponse(
                "Kurti",
                "Ladies Wear",
                "Ethnic",
                "Women",
                "Adult",
                "Maroon",
                List.of("Gold"),
                0.9,
                "Solid",
                0.8,
                "Cotton",
                0.85,
                "3/4",
                "Round",
                null,
                "Regular",
                "Festive",
                "Ethnic",
                "Beautiful cotton kurti",
                List.of("Maroon Cotton Kurti"),
                List.of(),
                List.of("kurti"),
                0.88,
                List.of(),
                "mock",
                "mock",
                null);
        String details = WholesaleProductAutofillService.composeDetails(ai);
        assertThat(details).contains("Material: Cotton");
        assertThat(details).contains("Pattern: Solid");
        assertThat(details).contains("Secondary colours: Gold");
    }

    @Test
    void guessColorHexMatchesCommonNames() {
        assertThat(WholesaleProductAutofillService.guessColorHex("Navy Blue")).isEqualTo("#1e3a8a");
        assertThat(WholesaleProductAutofillService.guessColorHex("unknown")).isNull();
    }

    @Test
    void womenDoesNotTokenMatchMen() {
        assertThat(WholesaleProductAutofillService.tokenContains("women", "men")).isFalse();
        assertThat(WholesaleProductAutofillService.tokenContains("mens wear", "men")).isTrue();
        assertThat(WholesaleProductAutofillService.tokenContains("ladies wear", "ladies")).isTrue();
        assertThat(WholesaleProductAutofillService.tokenContains("women casual top", "women")).isTrue();
    }
}
