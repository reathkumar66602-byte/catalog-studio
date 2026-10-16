package com.catalogstudio.wholesale.dto;

import java.util.UUID;

public record ProductImageResponse(UUID id, String url, String colorLabel, int sortOrder) {}