package com.catalogstudio.trending.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.trending.dto.TrendingCategoryResponse;
import com.catalogstudio.trending.dto.TrendingPageResponse;
import com.catalogstudio.trending.service.TrendingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trending")
@RequiredArgsConstructor
public class TrendingController {

    private final TrendingService trendingService;

    @GetMapping("/categories")
    public ApiResponse<List<TrendingCategoryResponse>> categories() {
        return ApiResponse.ok(trendingService.categories());
    }

    @GetMapping("/products")
    public ApiResponse<TrendingPageResponse> products(
            @RequestParam String marketplace,
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(required = false) Integer page
    ) {
        return ApiResponse.ok(trendingService.products(
                SecurityUtils.currentUserId(),
                marketplace,
                category,
                page));
    }
}
