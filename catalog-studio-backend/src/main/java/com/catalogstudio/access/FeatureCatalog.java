package com.catalogstudio.access;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class FeatureCatalog {

    public static final List<FeatureDefinition> ALL = List.of(
            new FeatureDefinition("dashboard", "Dashboard", "Home cards and workspace overview", "/dashboard", true),
            new FeatureDefinition("trending", "Trending products", "Flipkart, Meesho, and Amazon trending products", "/trending", false),
            new FeatureDefinition("shoot", "Shoot", "Catalog photo shoot from a garment photo", "/shoot", false),
            new FeatureDefinition("labels", "Label tools", "Flipkart, Meesho, and merge PDF tools", "/tools/labels", false),
            new FeatureDefinition("meesho_calculator", "Meesho calculator", "Profit calculator on the dashboard and menu", "/tools/meesho-calculator", false),
            new FeatureDefinition("extension", "Chrome Extension", "Pair and manage Catalog Studio Autofill", "/extension", false),
            new FeatureDefinition("subscription", "Subscription", "Plan, trial, and recharge", "/subscription", true),
            new FeatureDefinition("transactions", "Transactions", "Payment and activation history", "/transactions", false),
            new FeatureDefinition("analysis", "Analysis History", "Photo analysis history", "/analysis", false),
            new FeatureDefinition("billing_address", "Billing address", "GST and billing address", "/billing-address", false),
            new FeatureDefinition("settings", "Settings", "Profile, password, and language", "/settings", true)
    );

    public static final Set<String> KEYS = ALL.stream().map(FeatureDefinition::key).collect(Collectors.toUnmodifiableSet());

    private FeatureCatalog() {}

    public static Map<String, Boolean> defaultsEnabled() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (FeatureDefinition feature : ALL) {
            map.put(feature.key(), true);
        }
        return map;
    }

    public record FeatureDefinition(String key, String label, String description, String path, boolean required) {}
}
