package com.catalogstudio.trending.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AmazonTrendingClient implements TrendingMarketplaceClient {

    @Override
    public String marketplace() {
        return "AMAZON";
    }

    @Override
    public TrendingBatch nextBatch(TrendingCatalog.Category category, String cursor, int nextPage) {
        int page = Math.max(nextPage, 1);
        String node = category.amazonNode() == null ? "" : category.amazonNode();
        String url;
        List<TrendingHit> hits;
        if (node.startsWith("q:")) {
            String query = URLEncoder.encode(node.substring(2), StandardCharsets.UTF_8);
            url = "https://www.amazon.in/s?k=" + query + "&page=" + page;
            hits = AmazonSearchParser.parse(MarketplaceHttp.get("Amazon", url));
        } else {
            url = "https://www.amazon.in/gp/bestsellers" + node + "?pg=" + page;
            hits = AmazonBestsellersParser.parse(MarketplaceHttp.get("Amazon", url));
        }
        log.info("Amazon trending category={} page={} products={}", category.key(), page, hits.size());
        return new TrendingBatch(hits, null, page + 1);
    }
}
