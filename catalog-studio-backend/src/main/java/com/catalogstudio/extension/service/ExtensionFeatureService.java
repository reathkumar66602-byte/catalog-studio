package com.catalogstudio.extension.service;

import com.catalogstudio.analysis.repository.ProductAnalysisRepository;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.extension.dto.CompetitorAnalyzeRequest;
import com.catalogstudio.extension.dto.FillGapsRequest;
import com.catalogstudio.extension.dto.VerifyShopRequest;
import com.catalogstudio.extension.entity.ExtensionInventoryPhoto;
import com.catalogstudio.extension.entity.ExtensionUserSettings;
import com.catalogstudio.extension.repository.ExtensionInventoryPhotoRepository;
import com.catalogstudio.extension.repository.ExtensionUserSettingsRepository;
import com.catalogstudio.product.entity.ListingTemplate;
import com.catalogstudio.product.repository.ListingTemplateRepository;
import com.catalogstudio.storage.StorageService;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ExtensionFeatureService {

    private static final List<Map<String, String>> FALLBACK_CATEGORIES = List.of(
            Map.of("id", "women-kurti", "name", "Women Kurti", "path", "Women / Ethnic Wear / Kurti"),
            Map.of("id", "women-kurti-set", "name", "Women Kurti Set", "path", "Women / Ethnic Wear / Kurti Set"),
            Map.of("id", "saree", "name", "Saree", "path", "Women / Ethnic Wear / Saree"),
            Map.of("id", "dress", "name", "Women Dress", "path", "Women / Western Wear / Dress"),
            Map.of("id", "top", "name", "Women Top", "path", "Women / Western Wear / Top"),
            Map.of("id", "tshirt", "name", "T-Shirt", "path", "Clothing / T-Shirts"),
            Map.of("id", "men-shirt", "name", "Men Shirt", "path", "Men / Casual Wear / Shirt"),
            Map.of("id", "kids-frock", "name", "Kids Frock", "path", "Kids / Girls / Frock")
    );

    private final ExtensionService extensionService;
    private final ExtensionUserSettingsRepository settingsRepository;
    private final ExtensionInventoryPhotoRepository photoRepository;
    private final ListingTemplateRepository templateRepository;
    private final ProductAnalysisRepository analysisRepository;
    private final SubscriptionAccessService subscriptionAccessService;
    private final UserRepository userRepository;
    private final StorageService storageService;

    @Transactional
    public Map<String, Object> ping(String pairingKey) {
        Long userId = extensionService.requireUserId(pairingKey);
        extensionService.heartbeat(pairingKey);
        Map<String, Object> settings = settingsOf(userId);
        Map<String, Object> quota = quotaOf(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("quota", quota);
        out.put("plan", quota.get("plan"));
        out.put("shop", Map.of(
                "lockedName", str(settings.get("lockedShopName")),
                "lockedUid", str(settings.get("lockedShopUid"))
        ));
        out.put("settings", publicSettings(settings));
        return out;
    }

    @Transactional
    public Map<String, Object> verifyShop(String pairingKey, VerifyShopRequest request) {
        Long userId = extensionService.requireUserId(pairingKey);
        Map<String, Object> settings = settingsOf(userId);
        String incoming = usableShopName(request == null ? null : request.name());
        String uid = trim(request == null ? null : request.uid());
        String lockedName = trim(str(settings.get("lockedShopName")));
        String lockedUid = trim(str(settings.get("lockedShopUid")));
        boolean poisonedLock = isPageChromeName(lockedName);
        if (poisonedLock) {
            lockedName = "";
            lockedUid = "";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (incoming.isEmpty()) {
            if (poisonedLock) {
                settings.put("lockedShopName", "");
                settings.put("lockedShopUid", "");
                saveSettings(userId, settings);
            }
            out.put("status", "wait");
            out.put("registered", lockedName);
            return out;
        }
        if (lockedName.isEmpty()) {
            settings.put("lockedShopName", incoming);
            if (!uid.isEmpty()) {
                settings.put("lockedShopUid", uid);
            } else if (poisonedLock) {
                settings.put("lockedShopUid", "");
            }
            saveSettings(userId, settings);
            out.put("status", "ok");
            out.put("registered", incoming);
            return out;
        }
        boolean nameOk = namesMatch(lockedName, incoming);
        boolean uidOk = lockedUid.isEmpty() || uid.isEmpty() || lockedUid.equalsIgnoreCase(uid);
        if (nameOk && uidOk) {
            out.put("status", "ok");
            out.put("registered", lockedName);
            return out;
        }
        out.put("status", "bad");
        out.put("registered", lockedName);
        out.put("current", incoming);
        out.put("code", "lock_mismatch");
        out.put("error", "This Meesho shop does not match the Catalog Studio account lock (" + lockedName + ").");
        return out;
    }

    public void assertShopAllowed(String pairingKey, String name, String uid) {
        String usable = usableShopName(name);
        if (usable.isEmpty() && (uid == null || uid.isBlank())) {
            return;
        }
        Map<String, Object> verdict = verifyShop(pairingKey, new VerifyShopRequest(usable, uid));
        if ("bad".equals(verdict.get("status"))) {
            throw ApiException.forbidden(String.valueOf(verdict.get("error")));
        }
    }

    @Transactional
    public Map<String, Object> settings(String pairingKey) {
        Long userId = extensionService.requireUserId(pairingKey);
        return publicSettings(settingsOf(userId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> workspaceForUser(Long userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("settings", publicSettings(settingsOf(userId)));
        out.put("quota", quotaOf(userId));
        return out;
    }

    @Transactional
    public Map<String, Object> saveSettingsForUser(Long userId, Map<String, Object> incoming) {
        Map<String, Object> current = settingsOf(userId);
        applyIncoming(current, incoming);
        saveSettings(userId, current);
        return publicSettings(current);
    }

    @Transactional
    public Map<String, Object> saveSettings(String pairingKey, Map<String, Object> incoming) {
        Long userId = extensionService.requireUserId(pairingKey);
        return saveSettingsForUser(userId, incoming);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> categories(String pairingKey) {
        Long userId = extensionService.requireUserId(pairingKey);
        List<Map<String, Object>> out = new ArrayList<>();
        for (ListingTemplate template : templateRepository.findByUserId(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", template.getUuid().toString());
            row.put("name", template.getName());
            row.put("path", template.getName());
            row.put("productType", template.getProductType());
            row.put("attributes", template.getTemplateJson());
            row.put("source", "template");
            out.add(row);
        }
        for (Map<String, String> fallback : FALLBACK_CATEGORIES) {
            Map<String, Object> row = new LinkedHashMap<>(fallback);
            row.put("source", "catalog");
            out.add(row);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> matchCategory(String pairingKey, String path) {
        String needle = trim(path).toLowerCase(Locale.ROOT);
        List<Map<String, Object>> all = categories(pairingKey);
        Map<String, Object> best = null;
        int bestScore = 0;
        for (Map<String, Object> row : all) {
            String blob = (str(row.get("name")) + " " + str(row.get("path")) + " " + str(row.get("productType")))
                    .toLowerCase(Locale.ROOT);
            int score = 0;
            if (!needle.isEmpty() && blob.contains(needle)) {
                score += 8;
            }
            for (String token : needle.split("[^a-z0-9]+")) {
                if (token.length() > 2 && blob.contains(token)) {
                    score += 2;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = row;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matched", best != null && bestScore >= 2);
        out.put("category", best);
        return out;
    }

    public Map<String, Object> fillGaps(String pairingKey, FillGapsRequest request) {
        extensionService.requireUserId(pairingKey);
        Map<String, Object> current = request == null || request.current() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.current());
        String category = request == null ? "" : str(request.category()) + " " + str(request.notes());
        Map<String, String> defaults = defaultsFor(category);
        List<String> missing = request == null || request.missing() == null ? List.of() : request.missing();
        if (missing.isEmpty()) {
            missing = new ArrayList<>(defaults.keySet());
        }
        Map<String, Object> filled = new LinkedHashMap<>();
        for (String key : missing) {
            String lookup = normalizeKey(key);
            Object existing = current.get(key);
            if (existing == null) {
                existing = current.get(lookup);
            }
            if (existing != null && !str(existing).isBlank()) {
                continue;
            }
            String value = defaults.getOrDefault(lookup, defaults.get(key));
            if (value != null && !value.isBlank()) {
                filled.put(key, value);
                current.put(key, value);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fields", filled);
        out.put("listing", current);
        return out;
    }

    public Map<String, Object> competitorAnalyze(String pairingKey, CompetitorAnalyzeRequest request) {
        extensionService.requireUserId(pairingKey);
        List<Map<String, Object>> items = request == null || request.items() == null ? List.of() : request.items();
        List<Map<String, Object>> insights = new ArrayList<>();
        for (int i = 0; i < Math.min(items.size(), 40); i++) {
            Map<String, Object> item = items.get(i);
            double price = num(item.get("price"));
            double ratings = num(item.get("ratings") != null ? item.get("ratings") : item.get("reviews"));
            double rating = num(item.get("rating"));
            double pieces = ratings * 4;
            double sale = pieces * price;
            String badge = rating >= 4.2 && ratings > 500 ? "Best-selling"
                    : price > 0 && price < 299 ? "Sasta"
                    : sale > 1_000_000 ? "Top sale"
                    : "Watch";
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("index", i);
            row.put("badge", badge);
            row.put("estPieces", Math.round(pieces));
            row.put("estSale", Math.round(sale));
            row.put("reason", badge + " from public ratings × 4 as an order estimate. Not Meesho private data.");
            insights.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pageType", request == null ? "" : str(request.pageType()));
        out.put("insights", insights);
        out.put("summary", insights.isEmpty()
                ? "No product cards were sent. Scan a Meesho search or category page."
                : "Ranked " + insights.size() + " visible cards. Figures are estimates from public ratings.");
        return out;
    }

    @Transactional
    public Map<String, Object> pushPhoto(String pairingKey, String sourceId, MultipartFile thumb) {
        Long userId = extensionService.requireUserId(pairingKey);
        String id = trim(sourceId);
        if (id.isEmpty()) {
            throw ApiException.badRequest("sourceId is required");
        }
        var existing = photoRepository.findByUserIdAndSourceId(userId, id);
        if (existing.isPresent()) {
            return Map.of("created", false, "sourceId", id, "url", str(existing.get().getThumbUrl()));
        }
        StorageService.StoredFile stored = storageService.store(thumb);
        ExtensionInventoryPhoto photo = photoRepository.save(ExtensionInventoryPhoto.builder()
                .user(userRepository.getReferenceById(userId))
                .sourceId(id)
                .thumbUrl(stored.publicUrl())
                .storageKey(stored.storageKey())
                .build());
        return Map.of("created", true, "sourceId", id, "url", str(photo.getThumbUrl()));
    }

    private Map<String, Object> quotaOf(Long userId) {
        SubscriptionStatusResponse access = subscriptionAccessService.statusOf(userId);
        String plan = access.plan();
        int limit = 10;
        if (access.accessEntitled()) {
            Object monthly = access.features() == null ? null : access.features().get("monthlyAiAnalyses");
            if (monthly instanceof Number number) {
                limit = number.intValue();
            }
        } else {
            limit = 0;
        }
        Instant monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long used = analysisRepository.countByUserIdAndCreatedAtAfter(userId, monthStart);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plan", plan);
        out.put("used", used);
        out.put("limit", limit);
        out.put("remaining", Math.max(0, limit - used));
        out.put("requiresRecharge", access.requiresRecharge());
        return out;
    }

    private Map<String, Object> settingsOf(Long userId) {
        Map<String, Object> settings = settingsRepository.findById(userId)
                .map(row -> row.getSettingsJson() == null ? defaultsSettings() : new LinkedHashMap<>(row.getSettingsJson()))
                .orElseGet(this::defaultsSettings);
        if (isPageChromeName(trim(str(settings.get("lockedShopName"))))) {
            settings.put("lockedShopName", "");
            settings.put("lockedShopUid", "");
        }
        return settings;
    }

    private void saveSettings(Long userId, Map<String, Object> settings) {
        ExtensionUserSettings row = settingsRepository.findById(userId).orElseGet(() -> ExtensionUserSettings.builder()
                .user(userRepository.getReferenceById(userId))
                .build());
        row.setSettingsJson(settings);
        settingsRepository.save(row);
    }

    private void applyIncoming(Map<String, Object> current, Map<String, Object> incoming) {
        if (incoming == null) {
            return;
        }
        merge(current, incoming, "priceRule");
        merge(current, incoming, "packaging");
        if (incoming.get("keywords") instanceof List<?> keywords) {
            current.put("keywords", keywords.stream().map(String::valueOf).map(this::trim).filter(s -> !s.isEmpty()).limit(40).toList());
        }
        if (incoming.get("manufacturerProfiles") instanceof List<?> profiles) {
            current.put("manufacturerProfiles", profiles);
        }
        if (incoming.get("sizeCharts") instanceof Map<?, ?> charts) {
            current.put("sizeCharts", charts);
        }
        if (incoming.get("mfrProfileIndex") instanceof Number index) {
            current.put("mfrProfileIndex", index.intValue());
        }
        if (incoming.get("lockedShopName") instanceof String lockedShopName) {
            current.put("lockedShopName", usableShopName(lockedShopName));
        }
        if (incoming.get("lockedShopUid") instanceof String lockedShopUid) {
            current.put("lockedShopUid", trim(lockedShopUid));
        }
    }

    private Map<String, Object> publicSettings(Map<String, Object> settings) {
        Map<String, Object> out = new LinkedHashMap<>(defaultsSettings());
        out.putAll(settings);
        return out;
    }

    private Map<String, Object> defaultsSettings() {
        Map<String, Object> price = new LinkedHashMap<>();
        price.put("retCut", 1);
        price.put("mrpMul", 1.9);
        price.put("inventory", 200);
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("type", "Box");
        pack.put("length", "28");
        pack.put("width", "22");
        pack.put("height", "6");
        pack.put("weight", "250");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("priceRule", price);
        out.put("packaging", pack);
        out.put("keywords", List.of());
        out.put("manufacturerProfiles", List.of());
        out.put("sizeCharts", Map.of());
        out.put("mfrProfileIndex", 0);
        out.put("lockedShopName", "");
        out.put("lockedShopUid", "");
        return out;
    }

    private Map<String, String> defaultsFor(String category) {
        String blob = category == null ? "" : category.toLowerCase(Locale.ROOT);
        Map<String, String> out = new LinkedHashMap<>();
        out.put("material", "Cotton");
        out.put("fabric", "Cotton");
        out.put("gst", "5");
        out.put("hsn", "61091000");
        out.put("netWeight", "250");
        out.put("countryOfOrigin", "India");
        out.put("comboOf", "Single");
        out.put("netQuantity", "1");
        out.put("occasion", "Casual");
        out.put("fit", "Regular");
        out.put("washCare", "Machine Wash");
        out.put("packagingType", "Box");
        out.put("packageLength", "28");
        out.put("packageWidth", "22");
        out.put("packageHeight", "6");
        out.put("packageWeight", "250");
        if (blob.contains("kurti")) {
            out.put("hsn", "61061000");
            out.put("genericName", "Kurti");
            out.put("sleeveLength", "Three-Quarter Sleeves");
        }
        if (blob.contains("saree")) {
            out.put("hsn", "54075290");
            out.put("genericName", "Saree");
            out.put("netWeight", "400");
        }
        if (blob.contains("shirt")) {
            out.put("genericName", "Shirt");
            out.put("sleeveLength", "Long Sleeves");
            out.put("neckType", "Shirt Collar");
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private void merge(Map<String, Object> current, Map<String, Object> incoming, String key) {
        if (incoming.get(key) instanceof Map<?, ?> map) {
            Map<String, Object> dest = current.get(key) instanceof Map<?, ?> existing
                    ? new LinkedHashMap<>((Map<String, Object>) existing)
                    : new LinkedHashMap<>();
            map.forEach((k, v) -> dest.put(String.valueOf(k), v));
            current.put(key, dest);
        }
    }

    private boolean namesMatch(String a, String b) {
        return normalizeName(a).equals(normalizeName(b));
    }

    private String usableShopName(String value) {
        String name = trim(value);
        return isPageChromeName(name) ? "" : name;
    }

    /** Meesho chrome like "Login to Meesho Supplier Panel" is not a shop name. */
    private boolean isPageChromeName(String value) {
        String compact = normalizeName(value);
        if (compact.isEmpty()) {
            return false;
        }
        return compact.contains("loginto")
                || compact.contains("signin")
                || compact.contains("supplierpanel")
                || compact.contains("sellerpanel")
                || compact.equals("meesho")
                || compact.equals("login");
    }

    private String normalizeName(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        String lower = key.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "fabric" -> "material";
            case "generic name" -> "genericName";
            case "net quantity", "net quantity (n)" -> "netQuantity";
            case "combo of" -> "comboOf";
            case "country of origin" -> "countryOfOrigin";
            case "net weight", "net weight (gms)" -> "netWeight";
            default -> key;
        };
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private double num(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(str(value).replace(",", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
