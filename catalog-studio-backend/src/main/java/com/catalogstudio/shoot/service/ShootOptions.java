package com.catalogstudio.shoot.service;

import com.catalogstudio.common.exception.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ShootOptions {

    public static final List<String> AGES = List.of(
            "0-1", "1-2", "3-4", "5-6", "7-8", "9-10", "11-12",
            "13-15", "16-19",
            "20-25", "26-32", "33-40", "41-50", "50+"
    );
    public static final List<String> ANGLE_ORDER = List.of("FRONT", "BACK", "SIDE", "SHOP");
    /** Auto lets the model pick a scene that flatters the garment; fixed scenes stay available. */
    public static final String DEFAULT_MEESHO_BACKGROUND = "AUTO";
    public static final List<String> MEESHO_BACKGROUNDS = List.of(
            "AUTO",
            "FESTIVE_HOME",
            "LIVING_ROOM",
            "COURTYARD"
    );
    private static final Set<String> AGE_SET = Set.copyOf(AGES);
    private static final Set<String> ANGLE_SET = Set.copyOf(ANGLE_ORDER);
    private static final Set<String> MEESHO_BACKGROUND_SET = Set.copyOf(MEESHO_BACKGROUNDS);

    private ShootOptions() {}

    public static String mode(String raw) {
        String mode = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("SINGLE") && !mode.equals("COMBO")) {
            throw ApiException.badRequest("Choose single product or combo");
        }
        return mode;
    }

    public static String modelAge(String raw) {
        String age = raw == null ? "" : raw.trim();
        if (!AGE_SET.contains(age)) {
            throw ApiException.badRequest("Choose a model age");
        }
        return age;
    }

    public static String meeshoBackground(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MEESHO_BACKGROUND;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!MEESHO_BACKGROUND_SET.contains(value)) {
            throw ApiException.badRequest("Choose a Meesho background");
        }
        return value;
    }

    public static List<String> angles(List<String> raw, boolean marketplace) {
        if (raw == null || raw.isEmpty()) {
            if (marketplace) {
                return List.of();
            }
            throw ApiException.badRequest("Choose at least one photo to generate");
        }
        List<String> selected = raw.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        for (String angle : selected) {
            if (!ANGLE_SET.contains(angle)) {
                throw ApiException.badRequest("Unknown photo angle: " + angle);
            }
        }
        List<String> ordered = ANGLE_ORDER.stream().filter(selected::contains).toList();
        if (ordered.isEmpty() && !marketplace) {
            throw ApiException.badRequest("Choose at least one photo to generate");
        }
        return ordered;
    }

    public static boolean childAge(String modelAge) {
        return switch (modelAge) {
            case "0-1", "1-2", "3-4", "5-6", "7-8", "9-10", "11-12" -> true;
            default -> false;
        };
    }

    public static boolean teenAge(String modelAge) {
        return "13-15".equals(modelAge) || "16-19".equals(modelAge);
    }
}
