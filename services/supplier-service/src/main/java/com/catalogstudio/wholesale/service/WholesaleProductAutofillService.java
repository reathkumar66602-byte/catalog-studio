package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.supplier.client.AiAutofillClient;
import com.catalogstudio.supplier.client.AiAutofillClient.AutofillAnalysis;
import com.catalogstudio.supplier.client.AiAutofillClient.ImagePayload;
import com.catalogstudio.supplier.config.SupplierProperties;
import com.catalogstudio.wholesale.dto.ProductAutofillResponse;
import com.catalogstudio.wholesale.entity.WholesaleCategory;
import com.catalogstudio.wholesale.repository.WholesaleCategoryRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Runs vision AI on product photos and returns form-ready fields for supplier inventory.
 * Does not persist catalog products or wholesale rows.
 */
@Service
@RequiredArgsConstructor
public class WholesaleProductAutofillService {

    private static final int MAX_AUTOFILL_IMAGES = 3;
    private static final Map<String, String> COLOR_HEX = Map.ofEntries(
            Map.entry("black", "#111111"),
            Map.entry("white", "#ffffff"),
            Map.entry("red", "#dc2626"),
            Map.entry("blue", "#2563eb"),
            Map.entry("navy", "#1e3a8a"),
            Map.entry("green", "#16a34a"),
            Map.entry("yellow", "#eab308"),
            Map.entry("orange", "#ea580c"),
            Map.entry("pink", "#ec4899"),
            Map.entry("purple", "#9333ea"),
            Map.entry("brown", "#92400e"),
            Map.entry("beige", "#d6c3a5"),
            Map.entry("grey", "#6b7280"),
            Map.entry("gray", "#6b7280"),
            Map.entry("maroon", "#7f1d1d"),
            Map.entry("gold", "#ca8a04"),
            Map.entry("silver", "#9ca3af"),
            Map.entry("cream", "#f5f0e6"),
            Map.entry("khaki", "#b8a47a"),
            Map.entry("teal", "#0d9488"));

    private final WholesaleAccessService accessService;
    private final WholesaleCategoryRepository categoryRepository;
    private final AiAutofillClient aiAutofillClient;
    private final SupplierProperties properties;

    public ProductAutofillResponse autofill(Long userId, List<MultipartFile> images) {
        accessService.requireSupplier(userId);
        List<MultipartFile> selected = validateAndTrim(images);
        List<ImagePayload> payloads = new ArrayList<>(selected.size());
        for (MultipartFile file : selected) {
            try {
                byte[] bytes = file.getBytes();
                if (bytes.length == 0) {
                    throw ApiException.badRequest("One of the selected images is empty");
                }
                String contentType = resolveContentType(file, bytes);
                payloads.add(new ImagePayload(bytes, contentType, file.getOriginalFilename()));
            } catch (IOException ex) {
                throw ApiException.badRequest("Could not read the uploaded image");
            }
        }

        AutofillAnalysis ai = aiAutofillClient.analyze(payloads);
        WholesaleCategory matched = matchCategory(ai);
        String title = firstNonBlank(
                firstOf(ai.suggestedTitles()),
                ai.productType(),
                ai.category());
        String description = firstNonBlank(
                ai.productDescription(),
                firstOf(ai.suggestedDescriptions()));
        String details = composeDetails(ai);
        String color = blankToNull(ai.primaryColor());
        return new ProductAutofillResponse(
                title,
                description,
                details,
                matched == null ? null : matched.getUuid(),
                matched == null ? null : matched.getName(),
                color,
                guessColorHex(color),
                blankToNull(ai.productType()),
                ai.overallConfidence());
    }

    private List<MultipartFile> validateAndTrim(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            throw ApiException.badRequest("At least one image is required");
        }
        int limit = Math.min(MAX_AUTOFILL_IMAGES, Math.max(1, properties.upload().maxImages()));
        if (images.size() > limit) {
            return images.subList(0, limit);
        }
        return images;
    }

    private String resolveContentType(MultipartFile file, byte[] bytes) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType.contains(";")) {
            contentType = contentType.substring(0, contentType.indexOf(';')).trim();
        }
        if ("image/jpg".equals(contentType) || "image/pjpeg".equals(contentType)) {
            contentType = "image/jpeg";
        }
        List<String> allowed = properties.upload().allowedContentTypes();
        if (allowed != null && !allowed.isEmpty() && StringUtils.hasText(contentType) && !allowed.contains(contentType)) {
            throw ApiException.badRequest("Only JPEG, PNG, or WebP images are supported");
        }
        if (!StringUtils.hasText(contentType)) {
            contentType = "image/jpeg";
        }
        if (bytes.length > properties.upload().maxFileBytes()) {
            throw ApiException.badRequest("Image exceeds the maximum allowed size");
        }
        return contentType;
    }

    private WholesaleCategory matchCategory(AutofillAnalysis ai) {
        List<WholesaleCategory> categories = categoryRepository.findByStatusOrderBySortOrderAscNameAsc("ACTIVE");
        if (categories.isEmpty()) {
            return null;
        }
        String[] hints = {
                ai.category(),
                ai.subCategory(),
                ai.productType(),
                ai.gender(),
                ai.ageGroup(),
                firstOf(ai.keywords())
        };
        boolean feminine = hintsFeminine(hints);
        boolean masculine = hintsMasculine(hints) && !feminine;
        WholesaleCategory best = null;
        int bestScore = 0;
        for (WholesaleCategory category : categories) {
            String name = normalize(category.getName());
            String slug = normalize(category.getSlug());
            boolean mensCat = tokenContains(name, "men") || tokenContains(slug, "men") || slug.contains("mens");
            boolean ladiesCat = tokenContains(name, "ladies") || tokenContains(name, "women")
                    || tokenContains(slug, "ladies") || tokenContains(slug, "women");
            if (feminine && mensCat) {
                continue;
            }
            if (masculine && ladiesCat) {
                continue;
            }
            int score = 0;
            for (String hint : hints) {
                String value = normalize(hint);
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                if (name.equals(value) || slug.equals(value.replace(' ', '-'))) {
                    score += 100;
                } else if (tokenContains(name, value) || (value.length() >= 4 && tokenContains(value, name))) {
                    score += 40;
                } else {
                    score += keywordBoost(name, slug, value);
                }
            }
            if (feminine && ladiesCat) {
                score += 80;
            }
            if (masculine && mensCat) {
                score += 80;
            }
            if (score > bestScore) {
                bestScore = score;
                best = category;
            }
        }
        return bestScore >= 20 ? best : fallbackGeneral(categories);
    }

    private static boolean hintsFeminine(String[] hints) {
        for (String hint : hints) {
            String value = normalize(hint);
            if (tokenContains(value, "women") || tokenContains(value, "womens")
                    || tokenContains(value, "woman") || tokenContains(value, "ladies")
                    || tokenContains(value, "lady") || tokenContains(value, "female")
                    || tokenContains(value, "girl") || tokenContains(value, "girls")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hintsMasculine(String[] hints) {
        for (String hint : hints) {
            String value = normalize(hint);
            if (tokenContains(value, "men") || tokenContains(value, "mens")
                    || tokenContains(value, "male") || tokenContains(value, "gentleman")
                    || tokenContains(value, "gents") || tokenContains(value, "boy")
                    || tokenContains(value, "boys")) {
                return true;
            }
        }
        return false;
    }

    private static int keywordBoost(String name, String slug, String hint) {
        Map<String, String[]> aliases = new LinkedHashMap<>();
        aliases.put("kids", new String[] {"kid", "child", "boys", "girls", "infant", "baby"});
        aliases.put("mens", new String[] {"men", "male", "gentleman", "gents"});
        aliases.put("ladies", new String[] {"women", "womens", "female", "lady", "girls", "ladies"});
        aliases.put("footwear", new String[] {"shoe", "shoes", "sandal", "slippers", "boot"});
        aliases.put("accessories", new String[] {"bag", "belt", "watch", "jewellery", "jewelry", "cap", "hat"});
        aliases.put("home", new String[] {"kitchen", "decor", "furnish", "utensil"});
        aliases.put("electronics", new String[] {"electronic", "gadget", "phone", "charger", "earbud", "appliance"});
        aliases.put("general", new String[] {"misc", "other", "merchandise"});
        int score = 0;
        for (Map.Entry<String, String[]> entry : aliases.entrySet()) {
            boolean categoryHit = tokenContains(name, entry.getKey()) || tokenContains(slug, entry.getKey())
                    || ("mens".equals(entry.getKey()) && (tokenContains(name, "men") || tokenContains(slug, "men")));
            if (!categoryHit) {
                continue;
            }
            if (tokenContains(hint, entry.getKey())) {
                score += 35;
            }
            for (String alias : entry.getValue()) {
                if (tokenContains(hint, alias)) {
                    score += 30;
                    break;
                }
            }
        }
        return score;
    }

    public static boolean tokenContains(String text, String token) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(token)) {
            return false;
        }
        String hay = " " + text.trim() + " ";
        String needle = " " + token.trim() + " ";
        return hay.contains(needle);
    }

    private static WholesaleCategory fallbackGeneral(List<WholesaleCategory> categories) {
        for (WholesaleCategory category : categories) {
            String slug = normalize(category.getSlug());
            if (slug.contains("general")) {
                return category;
            }
        }
        return categories.get(0);
    }

    public static String composeDetails(AutofillAnalysis ai) {
        List<String> lines = new ArrayList<>();
        addDetail(lines, "Material", ai.material());
        addDetail(lines, "Pattern", ai.pattern());
        addDetail(lines, "Sleeve", ai.sleeveType());
        addDetail(lines, "Neck", ai.neckType());
        addDetail(lines, "Collar", ai.collarType());
        addDetail(lines, "Fit", ai.fit());
        addDetail(lines, "Occasion", ai.occasion());
        addDetail(lines, "Style", ai.style());
        addDetail(lines, "Gender", ai.gender());
        addDetail(lines, "Age group", ai.ageGroup());
        addDetail(lines, "Length", ai.lengthMeters());
        if (ai.secondaryColors() != null && !ai.secondaryColors().isEmpty()) {
            lines.add("Secondary colours: " + String.join(", ", ai.secondaryColors()));
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private static void addDetail(List<String> lines, String label, String value) {
        if (StringUtils.hasText(value)) {
            lines.add(label + ": " + value.trim());
        }
    }

    public static String guessColorHex(String color) {
        if (!StringUtils.hasText(color)) {
            return null;
        }
        String normalized = color.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").trim();
        for (Map.Entry<String, String> entry : COLOR_HEX.entrySet()) {
            if (normalized.equals(entry.getKey()) || normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String firstOf(List<String> values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
