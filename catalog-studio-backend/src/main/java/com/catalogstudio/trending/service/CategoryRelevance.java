package com.catalogstudio.trending.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Keeps Meesho/Tingily results aligned with the selected department (Men / Women / Kids).
 * Shared leaf slugs like {@code shirts} often return the wrong gender on Tingily.
 */
public final class CategoryRelevance {

    private static final Pattern WOMEN = Pattern.compile(
            "(?i)\\b(women|woman|womens|ladies|lady|girl|girls|female)\\b");
    private static final Pattern MEN = Pattern.compile(
            "(?i)\\b(men|man|mens|boy|boys|male)\\b");
    private static final Pattern KIDS = Pattern.compile(
            "(?i)\\b(kid|kids|baby|babies|infant|toddler)\\b");

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
        return switch (dept) {
            case "Men" -> !womenHit;
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

    public static boolean isDepartmentChild(String categoryKey) {
        return department(categoryKey) != null && categoryKey != null && categoryKey.contains("--");
    }
}
