package com.catalogstudio.wholesale.service;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

/** Small slug / code helpers for the wholesale module. */
final class WholesaleSlugs {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private WholesaleSlugs() {}

    static String slugify(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength).replaceAll("-+$", "");
        }
        return normalized;
    }

    static String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM[RANDOM.nextInt(ALPHANUM.length)]);
        }
        return sb.toString();
    }

    static String orderNumber() {
        return "WS" + java.time.LocalDate.now(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + randomSuffix(7).toUpperCase(Locale.ROOT);
    }
}
