package com.catalogstudio.trending.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AmazonBestsellersParser {

    private static final Pattern ASIN = Pattern.compile("id=\"([A-Z0-9]{10})\"");
    private static final Pattern DP = Pattern.compile("/dp/([A-Z0-9]{10})");
    private static final Pattern TITLE = Pattern.compile("line-clamp-3_[^\"]*\">([^<]+)");
    private static final Pattern ALT = Pattern.compile("<img[^>]+alt=\"([^\"]+)\"");
    private static final Pattern IMAGE = Pattern.compile("<img[^>]+src=\"(https://[^\"]+)\"");
    private static final Pattern HREF = Pattern.compile("href=\"([^\"]+)\"");
    private static final Pattern PRICE = Pattern.compile("p13n-sc-price\">([^<]+)");
    private static final Pattern RATING = Pattern.compile("([0-9]+(?:\\.[0-9]+)?) out of 5 stars");
    private static final Pattern REVIEWS = Pattern.compile("a-size-small\">([0-9,]+)");

    private AmazonBestsellersParser() {}

    public static List<TrendingHit> parse(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        String[] parts = html.split("id=\"gridItemRoot\"");
        Map<String, TrendingHit> unique = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String chunk = parts[i].length() > 25000 ? parts[i].substring(0, 25000) : parts[i];
            String asin = group(ASIN, chunk);
            if (asin == null) {
                asin = group(DP, chunk);
            }
            if (asin == null || unique.containsKey(asin)) {
                continue;
            }
            String title = TrendingText.unescape(group(TITLE, chunk));
            if (title == null) {
                title = TrendingText.unescape(group(ALT, chunk));
            }
            if (title == null || title.isBlank()) {
                continue;
            }
            String href = group(HREF, chunk);
            if (href == null || !href.contains("/dp/")) {
                href = "/dp/" + asin;
            }
            String url = href.startsWith("http") ? href : "https://www.amazon.in" + href;
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
