package com.catalogstudio.trending.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlipkartTrendingClient implements TrendingMarketplaceClient {

    private static final String ENDPOINT = "https://2.rome.api.flipkart.com/api/4/page/fetch";

    private final ObjectMapper objectMapper;

    @Override
    public String marketplace() {
        return "FLIPKART";
    }

    @Override
    public TrendingBatch nextBatch(TrendingCatalog.Category category, String cursor, int nextPage) {
        int page = Math.max(nextPage, 1);
        List<TrendingHit> hits;
        try {
            hits = fetch(category, page, true);
        } catch (RuntimeException ex) {
            hits = List.of();
        }
        if (hits.isEmpty()) {
            hits = fetch(category, page, false);
        }
        log.info("Flipkart trending category={} page={} products={}", category.key(), page, hits.size());
        return new TrendingBatch(hits, null, page + 1);
    }

    private List<TrendingHit> fetch(TrendingCatalog.Category category, int page, boolean popularity) {
        String query = URLEncoder.encode(category.flipkartQuery(), StandardCharsets.UTF_8);
        String pageUri = "/search?q=" + query + "&page=" + page + (popularity ? "&sort=popularity" : "");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("pageUri", pageUri);
        body.putObject("pageContext").put("fetchSeoData", false);
        ObjectNode requestContext = body.putObject("requestContext");
        requestContext.put("type", "BROWSE_PAGE");
        String json = MarketplaceHttp.postJson(
                "Flipkart",
                ENDPOINT,
                body.toString(),
                "https://www.flipkart.com",
                "https://www.flipkart.com/",
                "X-User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 FKUA/website/42/website/Desktop");
        try {
            JsonNode root = objectMapper.readTree(json);
            return FlipkartSearchParser.parse(root);
        } catch (Exception ex) {
            log.warn("Flipkart trending response could not be read");
            return List.of();
        }
    }
}
