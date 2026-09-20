package com.catalogstudio.user;

import java.util.Set;

public final class SupportedLocales {

    public static final String DEFAULT = "en";
    public static final Set<String> CODES = Set.of("en", "hi", "bn", "mr", "ta", "kn", "pa", "ur", "ja", "zh");

    private SupportedLocales() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String code = raw.trim().toLowerCase().replace('_', '-');
        int dash = code.indexOf('-');
        if (dash > 0) {
            code = code.substring(0, dash);
        }
        return CODES.contains(code) ? code : DEFAULT;
    }
}
