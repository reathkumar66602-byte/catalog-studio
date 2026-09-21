package com.catalogstudio.ai.provider;

import com.catalogstudio.ai.AIProductAnalysisService;
import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisResponse;
import com.catalogstudio.ai.AIProvider;
import com.catalogstudio.ai.VisionModelClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MockVisionProvider implements VisionModelClient {

    private static final Pattern COLOR = Pattern.compile(
            "\\b(navy(?:\\s+blue)?|maroon|burgundy|wine|red|pink|black|white|green|olive|teal|blue|yellow|orange|purple|grey|gray|brown|beige|cream|rust|mustard|peach)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public AIProvider provider() {
        return AIProvider.MOCK;
    }

    @Override
    public ProductAnalysisResponse analyze(AIProductAnalysisService.ProductAnalysisRequest request, String prompt) {
        String blob = ((request.categoryHint() == null ? "" : request.categoryHint()) + " "
                + (request.productTypeHint() == null ? "" : request.productTypeHint())).toLowerCase(Locale.ROOT);
        boolean women = blob.matches(".*\\b(wom[ae]n|ladies|female|girls?|kurti|saree|tunic)\\b.*");
        boolean men = !women && blob.matches(".*\\b(men|male|boys?)\\b.*");
        String gender = women ? "Women" : men ? "Men" : "Unisex";
        String type = inferType(blob, request.productTypeHint());
        String color = firstColor(blob);
        String pattern = blob.contains("check") ? "Checked" : blob.contains("print") ? "Printed" : null;
        String audience = "Women".equals(gender) ? "Women's" : "Men".equals(gender) ? "Men's" : "";
        String label = join(audience, color, pattern, type);
        List<String> titles = new ArrayList<>();
        titles.add(clip(label.isBlank() ? type : label));
        titles.add(clip(join(audience, color, type)));
        titles.add(clip(join(color, type) + ("Unisex".equals(gender) ? "" : " for " + gender)));
        titles.add(clip(join("Stylish", audience, type)));
        String description = (label.isBlank() ? type : label) + " based on seller hints only. Mock vision does not read the photo.";
        return new ProductAnalysisResponse(
                type,
                women ? "Women Clothing" : men ? "Men Clothing" : "Clothing",
                type,
                gender,
                "Adult",
                color,
                List.of(),
                color == null ? 0.2 : 0.7,
                pattern,
                pattern == null ? 0.2 : 0.7,
                null,
                0.42,
                blob.contains("full sleeve") || blob.contains("long sleeve") ? "Full Sleeve" : null,
                null,
                type.toLowerCase(Locale.ROOT).contains("shirt") ? "Shirt Collar" : null,
                "Regular Fit",
                "Casual",
                "Casual",
                description,
                titles.stream().filter(s -> s != null && s.length() >= 4).distinct().limit(4).toList(),
                List.of(description),
                List.of(type.toLowerCase(Locale.ROOT)),
                0.35,
                List.of("material", "image"),
                "MOCK",
                "mock-vision-v1"
        );
    }

    private static String inferType(String blob, String hint) {
        if (blob.contains("kurti")) return "Kurti";
        if (blob.contains("saree")) return "Saree";
        if (blob.contains("tunic")) return "Tunic";
        if (blob.contains("dress")) return "Dress";
        if (blob.contains("shirt")) return "Shirt";
        if (hint != null && !hint.isBlank() && hint.trim().length() <= 24 && !hint.contains("/")) {
            return hint.trim();
        }
        return "Top";
    }

    private static String firstColor(String blob) {
        Matcher matcher = COLOR.matcher(blob);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).trim();
        if (value.equalsIgnoreCase("navy")) {
            return "Navy Blue";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String join(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(part.trim());
        }
        return builder.toString();
    }

    private static String clip(String value) {
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 60 ? cleaned.substring(0, 60).trim() : cleaned;
    }
}
