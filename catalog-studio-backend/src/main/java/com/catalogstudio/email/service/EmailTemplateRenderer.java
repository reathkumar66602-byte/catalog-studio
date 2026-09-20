package com.catalogstudio.email.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmailTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    private EmailTemplateRenderer() {}

    public static String render(String template, Map<String, String> variables) {
        if (template == null || template.isBlank()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.getOrDefault(key, "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
