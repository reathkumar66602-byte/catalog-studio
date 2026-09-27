package com.catalogstudio.trending;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.trending.service.CategoryRelevance;
import com.catalogstudio.trending.service.TrendingHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryRelevanceTest {

    @Test
    void rejectsWomenTitlesForMenShirts() {
        assertThat(CategoryRelevance.matches("men--shirts", "Fancy Glamorous Women Shirts")).isFalse();
        assertThat(CategoryRelevance.matches("men--shirts", "Exclusive Lining Shirt Tunic")).isFalse();
        assertThat(CategoryRelevance.matches("men--shirts", "delta crop shart coat")).isFalse();
        assertThat(CategoryRelevance.matches("men--shirts", "DESIGNER SHIRT")).isFalse();
        assertThat(CategoryRelevance.matches("men--shirts", "Men Formal Cotton Shirt")).isTrue();
        assertThat(CategoryRelevance.mostlyMismatched(
                "men--shirts",
                List.of(
                        "Shart of peach",
                        "DESIGNER SHIRT",
                        "Exclusive Lining Shirt Tunic"))).isTrue();
    }

    @Test
    void keepsWomenTitlesForWomenShirts() {
        assertThat(CategoryRelevance.matches("women-western--shirts", "Fancy Glamorous Women Shirts")).isTrue();
        assertThat(CategoryRelevance.matches("women-western--shirts", "Men Formal Cotton Shirt")).isFalse();
    }

    @Test
    void filtersBatchToDepartment() {
        List<TrendingHit> hits = List.of(
                hit("Fancy Glamorous Women Shirts"),
                hit("Men Formal Cotton Shirt"),
                hit("Stylish Fashionable Women Shirts"),
                hit("DESIGNER SHIRT"));
        List<TrendingHit> men = CategoryRelevance.filter("men--shirts", hits, 10);
        assertThat(men).extracting(TrendingHit::title).containsExactly("Men Formal Cotton Shirt");
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
                "https://images.meesho.com/x.jpg",
                "https://www.meesho.com/p/1");
    }
}
