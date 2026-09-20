package com.catalogstudio.places.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlaceSuggestion(String placeId, String description) {
    public record PlaceDetails(
            String placeId,
            String formattedAddress,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country,
            BigDecimal latitude,
            BigDecimal longitude
    ) {}

    public record AutocompleteResponse(List<PlaceSuggestion> predictions) {}
}
