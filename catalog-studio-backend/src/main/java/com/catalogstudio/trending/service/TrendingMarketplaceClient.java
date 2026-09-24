package com.catalogstudio.trending.service;

public interface TrendingMarketplaceClient {

    String marketplace();

    TrendingBatch nextBatch(TrendingCatalog.Category category, String cursor, int nextPage);
}
