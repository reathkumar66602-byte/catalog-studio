package com.catalogstudio.trending.service;

import com.catalogstudio.common.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TrendingCatalog {

    public static final int PAGE_SIZE = 10;
    public static final int MAX_PAGE = 19;
    public static final Duration FEED_TTL = Duration.ofHours(6);

    private static final Map<String, String> AMAZON_NODES = Map.ofEntries(
            Map.entry("all", ""),
            Map.entry("popular", ""),
            Map.entry("beauty-health", "/beauty"),
            Map.entry("books", "/books"),
            Map.entry("car-motorbike", "/automotive"),
            Map.entry("electronics", "/electronics"),
            Map.entry("grocery", "/grocery"),
            Map.entry("home-kitchen", "/kitchen"),
            Map.entry("jewellery-accessories", "/jewelry"),
            Map.entry("kids-toys", "/toys"),
            Map.entry("musical-instruments", "/musical-instruments"),
            Map.entry("office-supplies-stationery", "/office-products"),
            Map.entry("pet-supplies", "/pet-supplies"),
            Map.entry("sports-fitness", "/sports"),
            Map.entry("watches", "/watches")
    );

    private static final Map<String, String> DEPARTMENT = Map.of(
            "men", "Men",
            "women-western", "Women",
            "lingerie", "Women",
            "kids-toys", "Kids",
            "kurti-saree-lehenga", "Women"
    );

    private static final List<Group> GROUPS = load();
    private static final Map<String, Category> BY_KEY = index(GROUPS);

    private TrendingCatalog() {}

    public static List<Group> groups() {
        return GROUPS;
    }

    public static Category requireCategory(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        Category category = BY_KEY.get(normalized);
        if (category == null) {
            throw ApiException.badRequest("Unknown category");
        }
        return category;
    }

    public static String requireMarketplace(String value) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("Choose Flipkart, Meesho, or Amazon");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("FLIPKART") && !normalized.equals("MEESHO") && !normalized.equals("AMAZON")) {
            throw ApiException.badRequest("Choose Flipkart, Meesho, or Amazon");
        }
        return normalized;
    }

    private static List<Group> load() {
        List<Group> groups = new ArrayList<>();
        groups.add(new Group(entry("all", "All products", "bestsellers", "", "bestseller"), List.of()));
        try (InputStream in = TrendingCatalog.class.getResourceAsStream("/trending/categories.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing trending category catalog");
            }
            JsonNode root = new ObjectMapper().readTree(in);
            for (JsonNode parent : root) {
                String slug = parent.path("slug").asText();
                String name = parent.path("name").asText();
                String parentQuery = "popular".equals(slug) ? "bestsellers" : name;
                String meesho = "popular".equals(slug) ? "bestseller" : name;
                Category head = entry(slug, name, parentQuery, amazonNode(slug, parentQuery), meesho);
                List<Category> children = new ArrayList<>();
                for (JsonNode child : parent.path("children")) {
                    String childName = child.path("name").asText("").trim();
                    String childSlug = child.path("slug").asText("").trim();
                    if (childName.isBlank() || childSlug.isBlank() || isGenericViewAll(childName)) {
                        continue;
                    }
                    String query = searchQuery(slug, name, childName);
                    children.add(entry(slug + "--" + childSlug, childName, query, "q:" + query, query));
                }
                groups.add(new Group(head, List.copyOf(children)));
            }
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
        return List.copyOf(groups);
    }

    private static Map<String, Category> index(List<Group> groups) {
        Map<String, Category> indexed = new LinkedHashMap<>();
        for (Group group : groups) {
            indexed.put(group.category().key(), group.category());
            for (Category child : group.children()) {
                indexed.put(child.key(), child);
            }
        }
        return Map.copyOf(indexed);
    }

    private static Category entry(String key, String label, String flipkartQuery, String amazonNode, String meeshoQuery) {
        return new Category(key, label, flipkartQuery, amazonNode, meeshoQuery);
    }

    private static String amazonNode(String key, String query) {
        return AMAZON_NODES.getOrDefault(key, "q:" + query);
    }

    private static boolean isGenericViewAll(String name) {
        return name.equalsIgnoreCase("View All") || name.equalsIgnoreCase("View All Books");
    }

    private static String searchQuery(String parentSlug, String parentName, String childName) {
        String child = childName;
        if (child.regionMatches(true, 0, "All ", 0, 4) && child.length() > 4) {
            child = child.substring(4).trim();
        }
        if (child.toLowerCase(Locale.ROOT).startsWith("view all")) {
            return parentName;
        }
        String department = DEPARTMENT.get(parentSlug);
        if (department == null || mentionsDepartment(child, department)) {
            return child;
        }
        return department + " " + child;
    }

    private static boolean mentionsDepartment(String child, String department) {
        String lower = child.toLowerCase(Locale.ROOT);
        if ("Men".equals(department)) {
            return lower.contains("men") || lower.contains("boy");
        }
        if ("Women".equals(department)) {
            return lower.contains("women") || lower.contains("ladies") || lower.contains("girl");
        }
        return lower.contains("kid") || lower.contains("baby") || lower.contains("boy") || lower.contains("girl");
    }

    public record Group(Category category, List<Category> children) {}

    public record Category(String key, String label, String flipkartQuery, String amazonNode, String meeshoQuery) {}
}
