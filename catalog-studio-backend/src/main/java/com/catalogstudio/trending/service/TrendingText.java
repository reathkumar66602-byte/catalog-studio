package com.catalogstudio.trending.service;

final class TrendingText {

    private TrendingText() {}

    static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }

    static String unescape(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#x26;", "&")
                .trim();
    }

    static String rupee(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        String trimmed = amount.trim();
        if (trimmed.startsWith("₹") || trimmed.startsWith("Rs")) {
            return clip(trimmed, 64);
        }
        if (trimmed.endsWith(".00")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        } else if (trimmed.endsWith(".0")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        }
        return clip("₹" + trimmed, 64);
    }
}
