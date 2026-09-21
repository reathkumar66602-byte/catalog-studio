package com.catalogstudio.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListingCopyNormalizerTest {

    @Test
    void rewritesMensTitlesWhenGenderIsWomen() {
        ProductAnalysisResponse ai = sample(
                "Women",
                "Navy Blue",
                "Checked",
                "Shirt",
                List.of("Men's Navy Blue Checked Casual Shirt", "Stylish Men's Navy Blue Check Shirt"),
                List.of("Men's navy blue checked casual shirt with a regular fit.")
        );
        ProductAnalysisResponse out = ListingCopyNormalizer.align(ai);
        assertThat(out.gender()).isEqualTo("Women");
        assertThat(out.suggestedTitles()).isNotEmpty();
        assertThat(out.suggestedTitles()).allMatch(title -> !title.toLowerCase().matches(".*\\bmen\\b.*"));
        assertThat(out.suggestedTitles().getFirst()).containsIgnoringCase("Women");
        assertThat(out.suggestedTitles().getFirst()).containsIgnoringCase("Navy");
        assertThat(out.suggestedDescriptions().getFirst()).containsIgnoringCase("Women");
    }

    @Test
    void keepsMatchingTitles() {
        ProductAnalysisResponse ai = sample(
                "Women",
                "Maroon",
                "Printed",
                "Kurti",
                List.of("Women's Maroon Printed Kurti"),
                List.of("Women's maroon printed kurti for daily wear.")
        );
        ProductAnalysisResponse out = ListingCopyNormalizer.align(ai);
        assertThat(out.suggestedTitles()).containsExactly("Women's Maroon Printed Kurti");
    }

    @Test
    void canonicalizesFemaleToWomen() {
        assertThat(ListingCopyNormalizer.canonicalGender("Female")).isEqualTo("Women");
        assertThat(ListingCopyNormalizer.canonicalGender("ladies")).isEqualTo("Women");
        assertThat(ListingCopyNormalizer.canonicalGender("male")).isEqualTo("Men");
    }

    @Test
    void detectsConflictingColorInTitle() {
        assertThat(ListingCopyNormalizer.colorMismatch("Men's Navy Blue Shirt", "Maroon")).isTrue();
        assertThat(ListingCopyNormalizer.colorMismatch("Women's Navy Blue Shirt", "Navy Blue")).isFalse();
    }

    private ProductAnalysisResponse sample(
            String gender,
            String color,
            String pattern,
            String type,
            List<String> titles,
            List<String> descriptions
    ) {
        return new ProductAnalysisResponse(
                type, "Clothing", type, gender, "Adult", color, List.of(), 0.9, pattern, 0.9,
                null, 0.4, "Full Sleeve", null, "Shirt Collar", "Regular Fit", "Casual", "Casual",
                descriptions.getFirst(), titles, descriptions, List.of(), 0.8, List.of(), "OPENAI", "gpt-4o-mini"
        );
    }
}
