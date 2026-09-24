package com.catalogstudio.trending.dto;

import java.util.List;

public record TrendingCategoryResponse(String key, String label, List<TrendingCategoryResponse> children) {}
