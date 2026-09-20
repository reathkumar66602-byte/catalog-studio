package com.catalogstudio.places.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.places.dto.PlaceSuggestion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class GooglePlacesService {

    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public List<PlaceSuggestion> autocomplete(String query) {
        if (!StringUtils.hasText(query) || query.trim().length() < 3) {
            return List.of();
        }
        String key = properties.google().mapsApiKey();
        if (!StringUtils.hasText(key)) {
            log.debug("GOOGLE_MAPS_API_KEY is not set; address suggestions are disabled");
            return List.of();
        }
        try {
            String url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input="
                    + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8)
                    + "&components=country:in&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            String status = root.path("status").asText();
            if (!"OK".equals(status) && !"ZERO_RESULTS".equals(status)) {
                log.warn("Google Places autocomplete status={} error={}", status, root.path("error_message").asText());
                throw ApiException.badRequest("Address lookup is temporarily unavailable");
            }
            List<PlaceSuggestion> out = new ArrayList<>();
            for (JsonNode prediction : root.path("predictions")) {
                out.add(new PlaceSuggestion(prediction.path("place_id").asText(), prediction.path("description").asText()));
            }
            return out;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Google Places autocomplete failed", ex);
            throw ApiException.badRequest("Could not look up that address");
        }
    }

    public PlaceSuggestion.PlaceDetails details(String placeId) {
        if (!StringUtils.hasText(placeId)) {
            throw ApiException.badRequest("Place id is required");
        }
        String key = properties.google().mapsApiKey();
        if (!StringUtils.hasText(key)) {
            throw ApiException.badRequest("Google Maps API key is not configured. Set GOOGLE_MAPS_API_KEY.");
        }
        try {
            String url = "https://maps.googleapis.com/maps/api/place/details/json?place_id="
                    + URLEncoder.encode(placeId, StandardCharsets.UTF_8)
                    + "&fields=address_component,formatted_address,geometry,place_id&key="
                    + URLEncoder.encode(key, StandardCharsets.UTF_8);
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            JsonNode result = objectMapper.readTree(body).path("result");
            AddressParts parts = parseComponents(result.path("address_components"));
            JsonNode location = result.path("geometry").path("location");
            return new PlaceSuggestion.PlaceDetails(
                    result.path("place_id").asText(placeId),
                    result.path("formatted_address").asText(""),
                    parts.line1,
                    parts.line2,
                    parts.city,
                    parts.state,
                    parts.postalCode,
                    parts.country,
                    decimal(location.path("lat")),
                    decimal(location.path("lng"))
            );
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Google Places details failed", ex);
            throw ApiException.badRequest("Could not load that address");
        }
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isNumber()) {
            return null;
        }
        return BigDecimal.valueOf(node.asDouble());
    }

    private static AddressParts parseComponents(JsonNode components) {
        String streetNumber = "";
        String route = "";
        String sublocality = "";
        String city = "";
        String state = "";
        String postal = "";
        String country = "India";
        for (JsonNode component : components) {
            List<String> types = new ArrayList<>();
            component.path("types").forEach(t -> types.add(t.asText()));
            String longName = component.path("long_name").asText("");
            if (types.contains("street_number")) {
                streetNumber = longName;
            } else if (types.contains("route")) {
                route = longName;
            } else if (types.contains("sublocality_level_1") || types.contains("sublocality")) {
                if (sublocality.isBlank()) {
                    sublocality = longName;
                }
            } else if (types.contains("locality") || types.contains("administrative_area_level_2")) {
                if (city.isBlank()) {
                    city = longName;
                }
            } else if (types.contains("administrative_area_level_1")) {
                state = longName;
            } else if (types.contains("postal_code")) {
                postal = longName;
            } else if (types.contains("country")) {
                country = longName;
            }
        }
        String line1 = (streetNumber + " " + route).trim();
        if (line1.isBlank()) {
            line1 = sublocality;
            sublocality = "";
        }
        return new AddressParts(line1, sublocality, city, state, postal, country);
    }

    private record AddressParts(String line1, String line2, String city, String state, String postalCode, String country) {}
}
