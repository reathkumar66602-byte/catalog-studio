package com.catalogstudio.wholesale.dto;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String slug, int productCount) {}