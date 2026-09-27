package com.catalogstudio.trending;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.trending.service.CategoryRelevance;
import com.catalogstudio.trending.service.MeeshoTrendingClient;
import com.catalogstudio.trending.service.TrendingHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryRelevanceTest {

    @Test
    void menShirtsKeepsShirtTitlesFromMenFeed() {
        assertThat(CategoryRelevance.matches("men--shirts", "Fancy Glamorous Women Shirts")).isFalse();
        assertThat(CategoryRelevance.matches("men--shirts", "Exclusive Lining Shirt Tunic")).isFalse();
        assertThat(CategoryRelevance.matches("men--shirts", "New Fashionable Formal Mens White shirt")).isTrue();
        assertThat(CategoryRelevance.matches("men--shirts", "Stylish Men White Winter Culting Sweatshirt")).isFalse();
    }

    @Test
    void womenShirtsStillWork() {
        assertThat(CategoryRelevance.matches("women-western--shirts", "Fancy Glamorous Women Shirts")).isTrue();
        assertThat(CategoryRelevance.matches("women-western--shirts", "Men Formal Cotton Shirt")).isFalse();
    }

    @Test
    void menShirtsUsesParentMenCatalogSlug() {
        assertThat(MeeshoTrendingClient.catalogSlug("men--shirts")).isEqualTo("men");
        assertThat(MeeshoTrendingClient.catalogSlug("women-western--shirts")).isEqualTo("shirts");
        assertThat(MeeshoTrendingClient.catalogSlug("men--summer-t-shirts")).isEqualTo("summer-t-shirts");
    }

    @Test
    void filtersBatch() {
        List<TrendingHit> hits = List.of(
                hit("Fancy Glamorous Women Shirts"),
                hit("New Fashionable Formal Mens White shirt"),
                hit("trandy solid full sleeve sweatshirt"));
        assertThat(CategoryRelevance.filter("men--shirts", hits, 10))
                .extracting(TrendingHit::title)
                .containsExactly("New Fashionable Formal Mens White shirt");
    }

    private static TrendingHit hit(String title) {
        return new TrendingHit(
                "id",
                title,
                null,
                "₹100",
                null,
                null,
                null,
                "https://images.meesho.com/images/products/1/x.jpg",
                "https://www.meesho.com/p/1");
    }
}
