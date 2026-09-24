package com.catalogstudio.trending.service;

import java.util.List;

public record TrendingBatch(List<TrendingHit> products, String cursor, int nextPage) {}
