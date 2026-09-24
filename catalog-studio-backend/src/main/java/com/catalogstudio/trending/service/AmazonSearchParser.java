package com.catalogstudio.trending.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AmazonSearchParser {

    private static final Pattern TITLE = Pattern.compile("<h2[^>]*>.*?<span[^>]*>([^<]+)</span>", Pattern.DOTALL);
    private static final Pattern PRICE = Pattern.compile("a-offscreen\">([^<]+)");
    private static final Pattern RATING = Pattern.compile("([0-9]+(?:\\.[0-9]+)?) out of 5 stars");
    private static final Pattern REVIEWS = Pattern.compile("a-size-base s-underline-text\">([0-9,]+)");
    private static final Pattern IMAGE = Pattern.compile("<img[^>]+src=\"(https://[^\"]+)\"");
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]*/dp/[A-Z0-9]{10}[^\"]*)\"");

    private AmazonSearchParser() {}

    public static List<TrendingHit> parse(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        String[] parts = html.split("data-asin=\"");
        Map<String, TrendingHit> unique = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String chunk = parts[i].length() > 20000 ? parts[i].substring(0, 20000) : parts[i];
            if (chunk.length() < 10) {
                continue;
            }
            String asin = chunk.substring(0, 10);
            if (!asin.matches("[A-Z0-9]{10}") || unique.containsKey(asin)) {
                continue;
            }
            String title = TrendingText.unescape(group(TITLE, chunk));
            if (title == null || title.isBlank()) {
                continue;
            }
            String href = group(HREF, chunk);
            String url = href == null
                    ? "https://www.amazon.in/dp/" + asin
                    : (href.startsWith("http") ? href : "https://www.amazon.in" + href);
            unique.put(asin, new TrendingHit(
                    asin,
                    TrendingText.clip(title, 500),
                    null,
                    TrendingText.clip(TrendingText.unescape(group(PRICE, chunk)), 64),
                    null,
                    group(RATING, chunk),
                    group(REVIEWS, chunk),
                    group(IMAGE, chunk),
                    url
            ));
        }
        return new ArrayList<>(unique.values());
    }

    private static String group(Pattern pattern, String chunk) {
        Matcher matcher = pattern.matcher(chunk);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
