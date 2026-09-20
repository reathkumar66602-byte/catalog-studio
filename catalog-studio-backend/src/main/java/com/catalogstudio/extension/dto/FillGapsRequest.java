package com.catalogstudio.extension.dto;

import java.util.List;
import java.util.Map;

public record FillGapsRequest(
        String category,
        String notes,
        Map<String, Object> current,
        List<String> missing
) {}
