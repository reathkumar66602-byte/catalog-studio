package com.catalogstudio.places.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.places.dto.PlaceSuggestion;
import com.catalogstudio.places.service.GooglePlacesService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Tag(name = "Places")
public class PlacesController {

    private final GooglePlacesService googlePlacesService;

    @GetMapping("/autocomplete")
    public ApiResponse<List<PlaceSuggestion>> autocomplete(@RequestParam String query) {
        return ApiResponse.ok(googlePlacesService.autocomplete(query));
    }

    @GetMapping("/details")
    public ApiResponse<PlaceSuggestion.PlaceDetails> details(@RequestParam String placeId) {
        return ApiResponse.ok(googlePlacesService.details(placeId));
    }
}
