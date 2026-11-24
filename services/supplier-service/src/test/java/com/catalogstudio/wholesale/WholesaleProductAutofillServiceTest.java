package com.catalogstudio.wholesale;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.supplier.client.AiAutofillClient.AutofillAnalysis;
import com.catalogstudio.wholesale.service.WholesaleProductAutofillService;
import java.util.List;
import org.junit.jupiter.api.Test;

class WholesaleProductAutofillServiceTest {

    @Test
    void composeDetailsIncludesKnownAttributes() {
        AutofillAnalysis ai = new AutofillAnalysis(
                "Ladies Wear",
                "Ethnic",
                "Women",
                "Adult",
                "Maroon",
                List.of("Gold"),
                "Solid",
                "Cotton",
                "3/4",
                "Round",
                null,
                "Regular",
                "Festive",
                "Ethnic",
                null,
                "Kurti",
                "Beautiful cotton kurti",
                List.of("Maroon Cotton Kurti"),
                List.of(),
                List.of("kurti"),
                0.88);
        String details = WholesaleProductAutofillService.composeDetails(ai);
        assertThat(details).contains("Material: Cotton");
        assertThat(details).contains("Pattern: Solid");
        assertThat(details).contains("Secondary colours: Gold");
    }

    @Test
    void guessColorHexMatchesCommonNames() {
        assertThat(WholesaleProductAutofillService.guessColorHex("Navy Blue")).isEqualTo("#1e3a8a");
        assertThat(WholesaleProductAutofillService.guessColorHex(null)).isNull();
    }

    @Test
    void tokenContainsDoesNotMatchMenInsideWomen() {
        assertThat(WholesaleProductAutofillService.tokenContains("women wear", "men")).isFalse();
        assertThat(WholesaleProductAutofillService.tokenContains("men wear", "men")).isTrue();
    }
}
