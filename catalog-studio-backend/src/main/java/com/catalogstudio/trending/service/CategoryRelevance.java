package com.catalogstudio.trending.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Keeps Meesho/Tingily results aligned with the selected department (Men / Women / Kids).
 * Shared leaf slugs like {@code shirts} often return the wrong gender on Tingily, including
 * women's items that omit the word "Women" (tunic, crop shirt, etc.).
 */
public final class CategoryRelevance {

    private static final Pattern WOMEN = Pattern.compile(
            "(?i)\\b(women|woman|womens|ladies|lady|girl|girls|female)\\b");
    private static final Pattern WOMEN_FASHION = Pattern.compile(
            "(?i)\\b(tunic|blouse|kurti|kurta\\s*set|saree|sari|lehenga|anarkali|gown|skirt|"
                    + "legging|jeggings|palazzo|crop\\s*top|crop\\s*shirt|crop\\s*shart|"
                    + "womenswear|ladieswear|for\\s+women|for\\s+girls?)\\b");
    private static final Pattern MEN = Pattern.compile(
            "(?i)\\b(men|man|mens|boy|boys|male)\\b");
    private static final Pattern KIDS = Pattern.compile(
            "(?i)\\b(kid|kids|baby|babies|infant|toddler)\\b");

    /** Leaf slugs Tingily shares across Men and Women (often women-dominated). */
    private static final Set<String> SHARED_FASHION_LEAVES = Set.of(
            "shirts",
            "t-shirts",
            "jeans",
            "jackets",
            "sweatshirts",
            "sweaters",
            "trackpants",
            "kurtas",
            "kurta-sets",
            "shirts-combo",
            "t-shirts-combos",
            "summer-t-shirts",
            "gym-tshirts",
            "nehru-jacket");

    private CategoryRelevance() {}

    public static String department(String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank()) {
            return null;
        }
        String key = categoryKey.trim().toLowerCase(Locale.ROOT);
        int split = key.indexOf("--");
        String parent = split >= 0 ? key.substring(0, split) : key;
        return switch (parent) {
            case "men" -> "Men";
            case "women-western", "lingerie", "kurti-saree-lehenga" -> "Women";
            case "kids-toys" -> "Kids";
            default -> null;
        };
    }

    public static String leafSlug(String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank() || "all".equals(categoryKey)) {
            return "popular";
        }
        int split = categoryKey.lastIndexOf("--");
        if (split >= 0 && split + 2 < categoryKey.length()) {
            return categoryKey.substring(split + 2);
        }
        return categoryKey;
    }

    public static boolean isDepartmentChild(String categoryKey) {
        return department(categoryKey) != null && categoryKey != null && categoryKey.contains("--");
    }

    /** Men/Women child leaves that must not trust the shared Tingily slug alone. */
    public static boolean requiresPositiveDepartmentMatch(String categoryKey) {
        String dept = department(categoryKey);
        if (!isDepartmentChild(categoryKey) || dept == null) {
            return false;
        }
        if ("Men".equals(dept)) {
            return SHARED_FASHION_LEAVES.contains(leafSlug(categoryKey))
                    || leafSlug(categoryKey).contains("shirt")
                    || leafSlug(categoryKey).contains("tshirt")
                    || leafSlug(categoryKey).contains("jacket");
        }
        return false;
    }

    public static boolean matches(String categoryKey, String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String dept = department(categoryKey);
        if (dept == null) {
            return true;
        }
        boolean womenHit = WOMEN.matcher(title).find() || WOMEN_FASHION.matcher(title).find();
        boolean menHit = MEN.matcher(title).find() && !WOMEN.matcher(title).find();
        return switch (dept) {
            case "Men" -> {
                if (womenHit) {
                    yield false;
                }
                if (requiresPositiveDepartmentMatch(categoryKey)) {
                    yield menHit;
                }
                yield true;
            }
            case "Women" -> !menHit || WOMEN.matcher(title).find();
            case "Kids" -> true;
            default -> true;
        };
    }

    public static boolean oppositeGender(String categoryKey, String title) {
        return title != null && !title.isBlank() && !matches(categoryKey, title);
    }

    public static List<TrendingHit> filter(String categoryKey, List<TrendingHit> hits, int max) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        if (department(categoryKey) == null) {
            return hits.size() <= max ? List.copyOf(hits) : List.copyOf(hits.subList(0, max));
        }
        List<TrendingHit> matched = new ArrayList<>();
        for (TrendingHit hit : hits) {
            if (matches(categoryKey, hit.title())) {
                matched.add(hit);
                if (matched.size() >= max) {
                    break;
                }
            }
        }
        return matched;
    }

    /** True when half or more titles contradict the selected department. */
    public static boolean mostlyMismatched(String categoryKey, List<String> titles) {
        if (department(categoryKey) == null || titles == null || titles.isEmpty()) {
            return false;
        }
        int bad = 0;
        for (String title : titles) {
            if (oppositeGender(categoryKey, title)) {
                bad++;
            }
        }
        return bad * 2 >= titles.size();
    }
}
