package com.catalogstudio.trending.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Light gender/type filter for Meesho categories. Keep this simple — other marketplaces
 * and non-fashion leaves pass through unchanged.
 */
public final class CategoryRelevance {

    private static final Pattern WOMEN = Pattern.compile(
            "(?i)\\b(women|woman|womens|ladies|lady|girl|girls|female|tunic|blouse|kurti|saree|lehenga)\\b");
    private static final Pattern MEN = Pattern.compile("(?i)\\b(men|man|mens|boy|boys)\\b");
    private static final Pattern SHIRT = Pattern.compile("(?i)\\bshirts?\\b|\\bsharts?\\b");
    private static final Pattern SWEATSHIRT = Pattern.compile("(?i)sweat\\s*shirt");
    private static final Pattern JACKET = Pattern.compile("(?i)\\bjackets?\\b");
    private static final Pattern SWEATER = Pattern.compile("(?i)\\bsweaters?\\b|\\bsweatshirts?\\b");
    private static final Pattern TSHIRT = Pattern.compile("(?i)\\bt[\\s-]?shirts?\\b|\\btees?\\b");

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
        if (categoryKey == null || categoryKey.isBlank()) {
            return "";
        }
        String key = categoryKey.trim().toLowerCase(Locale.ROOT);
        int split = key.lastIndexOf("--");
        return split >= 0 ? key.substring(split + 2) : key;
    }

    public static boolean isDepartmentChild(String categoryKey) {
        return department(categoryKey) != null && categoryKey != null && categoryKey.contains("--");
    }

    public static boolean matches(String categoryKey, String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String dept = department(categoryKey);
        if (dept == null) {
            return true;
        }
        boolean womenHit = WOMEN.matcher(title).find();
        boolean menHit = MEN.matcher(title).find() && !womenHit;
        String leaf = leafSlug(categoryKey);

        return switch (dept) {
            case "Men" -> {
                if (womenHit) {
                    yield false;
                }
                // When we load the parent "men" feed, keep only the selected product type.
                if ("shirts".equals(leaf)) {
                    yield SHIRT.matcher(title).find() && !SWEATSHIRT.matcher(title).find();
                }
                if ("jackets".equals(leaf)) {
                    yield JACKET.matcher(title).find();
                }
                if ("sweatshirts".equals(leaf) || "sweaters".equals(leaf)) {
                    yield SWEATER.matcher(title).find();
                }
                if ("t-shirts".equals(leaf)) {
                    yield TSHIRT.matcher(title).find();
                }
                yield true;
            }
            case "Women" -> !menHit || womenHit;
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
