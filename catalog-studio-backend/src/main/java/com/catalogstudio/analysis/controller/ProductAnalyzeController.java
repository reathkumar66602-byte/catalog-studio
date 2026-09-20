package com.catalogstudio.analysis.controller;

import com.catalogstudio.analysis.dto.AnalysisHistoryItem;
import com.catalogstudio.analysis.dto.AnalysisResultResponse;
import com.catalogstudio.analysis.entity.ProductAnalysis;
import com.catalogstudio.analysis.repository.ProductAnalysisRepository;
import com.catalogstudio.analysis.service.ProductAnalyzeService;
import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Product Analysis")
public class ProductAnalyzeController {

    private final ProductAnalyzeService productAnalyzeService;
    private final ProductAnalysisRepository analysisRepository;

    @PostMapping(value = "/product/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Analyze product images with AI vision")
    public ApiResponse<AnalysisResultResponse> analyze(
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam(value = "primaryIndex", required = false) Integer primaryIndex,
            @RequestParam(value = "marketplace", required = false) String marketplace,
            @RequestParam(value = "categoryHint", required = false) String categoryHint,
            @RequestParam(value = "productTypeHint", required = false) String productTypeHint
    ) {
        AnalysisResultResponse data = productAnalyzeService.analyze(
                SecurityUtils.currentUserId(), images, primaryIndex, marketplace, categoryHint, productTypeHint);
        return ApiResponse.ok("Product analyzed successfully", data);
    }

    @GetMapping("/analysis/{id}")
    public ApiResponse<AnalysisResultResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(productAnalyzeService.get(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/analysis")
    @Transactional(readOnly = true)
    public ApiResponse<Page<AnalysisHistoryItem>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductAnalysis> result = analysisRepository.findByUserId(
                SecurityUtils.currentUserId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.ok(result.map(AnalysisHistoryItem::from));
    }
}
