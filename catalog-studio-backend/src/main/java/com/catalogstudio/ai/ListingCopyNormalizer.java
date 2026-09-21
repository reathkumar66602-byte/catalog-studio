package com.catalogstudio.ai;

import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ListingCopyNormalizer {

    private static final Pattern MENS = Pattern.compile("\\bmen['’]?s\\b|\\bfor men\\b|\\bmale\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WOMENS = Pattern.compile("\\bwom[ae]n['’]?s\\b|\\bfor women\\b|\\blad[yie]s\\b|\\bfemale\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_WORD = Pattern.compile(
            "\\b(navy(?:\\s+blue)?|maroon|burgundy|wine|red|pink|black|white|green|olive|teal|blue|yellow|orange|purple|grey|gray|brown|beige|cream|rust|mustard|peach|magenta|turquoise|gold|silver|khaki|lavender|coral|off[-\\s]?white|sky blue|light blue|dark blue)\\b",
            Pattern.CASE_INSENSITIVE);

    private ListingCopyNormalizer() {}

    public static ProductAnalysisResponse align(ProductAnalysisResponse ai) {
        if (ai == null) {
            return null;
        }
        String gender = canonicalGender(ai.gender());
        List<String> titles = alignTitles(ai.suggestedTitles(), gender, ai.primaryColor(), ai.pattern(), ai.productType());
        List<String> descriptions = alignDescriptions(ai.suggestedDescriptions(), gender, ai.primaryColor());
        String productDescription = rewriteAudience(ai.productDescription(), gender);
        productDescription = rewriteColor(productDescription, ai.primaryColor());
        return new ProductAnalysisResponse(
                ai.productType(),
                ai.category(),
                ai.subCategory(),
                gender,
                ai.ageGroup(),
                ai.primaryColor(),
                ai.secondaryColors(),
                ai.colorConfidence(),
                ai.pattern(),
                ai.patternConfidence(),
                ai.material(),
                ai.materialConfidence(),
                ai.sleeveType(),
                ai.neckType(),
                ai.collarType(),
                ai.fit(),
                ai.occasion(),
                ai.style(),
                productDescription,
                titles,
                descriptions,
                ai.keywords(),
                ai.overallConfidence(),
                ai.uncertainFields(),
                ai.provider(),
                ai.model()
        );
    }

    static String canonicalGender(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return null;
        }
        if (text.matches(".*\\b(wom[ae]n|female|lad(y|ies)|girls?)\\b.*")) {
            return "Women";
        }
        if (text.matches(".*\\b(men|male|boys?)\\b.*")) {
            return "Men";
        }
        if (text.matches(".*\\b(kid|kids|child|children|infant|baby)\\b.*")) {
            return "Kids";
        }
        if (text.contains("unisex")) {
            return "Unisex";
        }
        if (text.equals("women") || text.equals("woman")) {
            return "Women";
        }
        return Character.toUpperCase(value.trim().charAt(0)) + value.trim().substring(1);
    }

    private static List<String> alignTitles(
            List<String> titles,
            String gender,
            String color,
            String pattern,
            String productType
    ) {
        List<String> source = titles == null ? List.of() : titles.stream().filter(s -> s != null && !s.isBlank()).toList();
        boolean mismatch = source.stream().anyMatch(title -> audienceMismatch(title, gender) || colorMismatch(title, color));
        if (!mismatch && !source.isEmpty()) {
            return source;
        }
        return buildTitles(gender, color, pattern, productType, source);
    }

    private static List<String> alignDescriptions(List<String> descriptions, String gender, String color) {
        if (descriptions == null || descriptions.isEmpty()) {
            return descriptions == null ? List.of() : descriptions;
        }
        List<String> out = new ArrayList<>();
        for (String description : descriptions) {
            out.add(rewriteColor(rewriteAudience(description, gender), color));
        }
        return out;
    }

    static boolean audienceMismatch(String title, String gender) {
        if (gender == null || title == null) {
            return false;
        }
        if ("Women".equals(gender) && MENS.matcher(title).find() && !WOMENS.matcher(title).find()) {
            return true;
        }
        if ("Men".equals(gender) && WOMENS.matcher(title).find() && !MENS.matcher(title).find()) {
            return true;
        }
        return false;
    }

    static boolean colorMismatch(String title, String color) {
        if (title == null || color == null || color.isBlank()) {
            return false;
        }
        var matcher = COLOR_WORD.matcher(title);
        if (!matcher.find()) {
            return false;
        }
        String found = matcher.group(1).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String expected = color.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (expected.contains(found) || found.contains(expected)) {
            return false;
        }
        String expectedCore = expected.replace("blue", "").trim();
        String foundCore = found.replace("blue", "").trim();
        return expectedCore.isEmpty() || foundCore.isEmpty() || !expected.contains(foundCore);
    }

    private static List<String> buildTitles(String gender, String color, String pattern, String productType, List<String> fallback) {
        String audience = audienceWord(gender);
        String type = blankTo(productType, "Top");
        String hue = blankTo(color, "");
        String print = blankTo(pattern, "");
        List<String> built = new ArrayList<>();
        addTitle(built, join(audience, hue, print, type));
        addTitle(built, join(audience, hue, type));
        addTitle(built, join(hue, print, type) + (gender == null ? "" : " for " + gender));
        addTitle(built, join("Stylish", audience, hue, type));
        if (built.isEmpty()) {
            return fallback.isEmpty() ? List.of(type) : fallback;
        }
        return built;
    }

    private static void addTitle(List<String> titles, String title) {
        String cleaned = title.replaceAll("\\s+", " ").trim();
        if (cleaned.length() < 8) {
            return;
        }
        String clipped = cleaned.length() > 60 ? cleaned.substring(0, 60).trim() : cleaned;
        if (titles.stream().noneMatch(existing -> existing.equalsIgnoreCase(clipped))) {
            titles.add(clipped);
        }
    }

    private static String rewriteAudience(String text, String gender) {
        if (text == null || gender == null) {
            return text;
        }
        if ("Women".equals(gender)) {
            return text.replaceAll("(?i)\\bmen['’]?s\\b", "Women's")
                    .replaceAll("(?i)\\bfor men\\b", "for women")
                    .replaceAll("(?i)\\bmale\\b", "women");
        }
        if ("Men".equals(gender)) {
            return text.replaceAll("(?i)\\bwom[ae]n['’]?s\\b", "Men's")
                    .replaceAll("(?i)\\bfor women\\b", "for men")
                    .replaceAll("(?i)\\bfemale\\b", "men");
        }
        return text;
    }

    private static String rewriteColor(String text, String color) {
        if (text == null || color == null || color.isBlank()) {
            return text;
        }
        if (!colorMismatch(text, color)) {
            return text;
        }
        String expected = color.toLowerCase(Locale.ROOT);
        return COLOR_WORD.matcher(text).replaceAll(match -> {
            String found = match.group(1).toLowerCase(Locale.ROOT);
            if (expected.contains(found) || found.contains(expected.replace("blue", "").trim())) {
                return match.group(0);
            }
            return color;
        });
    }

    private static String audienceWord(String gender) {
        if ("Women".equals(gender)) {
            return "Women's";
        }
        if ("Men".equals(gender)) {
            return "Men's";
        }
        if ("Kids".equals(gender)) {
            return "Kids";
        }
        return "";
    }

    private static String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
