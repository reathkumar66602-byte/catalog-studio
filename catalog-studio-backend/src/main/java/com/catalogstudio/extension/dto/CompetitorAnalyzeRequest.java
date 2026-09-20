package com.catalogstudio.extension.dto;

import java.util.List;
import java.util.Map;

public record CompetitorAnalyzeRequest(
        String pageType,
        List<Map<String, Object>> items
) {}
