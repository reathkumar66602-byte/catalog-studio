package com.catalogstudio.internal.controller;

import com.catalogstudio.ai.AIProductAnalysisService;
import com.catalogstudio.ai.AIProductAnalysisService.ImagePayload;
import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisRequest;
import com.catalogstudio.ai.AIProductAnalysisService.ProductAnalysisResponse;
import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.internal.InternalApiSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/internal/v1/ai")
@RequiredArgsConstructor
public class InternalAiController {

    private final InternalApiSupport internalApiSupport;
    private final AIProductAnalysisService aiProductAnalysisService;

    @PostMapping(value = "/wholesale-autofill", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> wholesaleAutofill(
            @RequestPart("images") List<MultipartFile> images,
            HttpServletRequest request) {
        internalApiSupport.requireInternalKey(request);
        if (images == null || images.isEmpty()) {
            throw ApiException.badRequest("At least one image is required");
        }
        List<ImagePayload> payloads = new ArrayList<>();
        for (MultipartFile file : images) {
            try {
                payloads.add(new ImagePayload(file.getBytes(), file.getContentType(), file.getOriginalFilename()));
            } catch (IOException ex) {
                throw ApiException.badRequest("Could not read uploaded image");
            }
        }
        ProductAnalysisResponse ai = aiProductAnalysisService.analyze(
                new ProductAnalysisRequest(payloads, "WHOLESALE", null, null));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("category", nullToEmpty(ai.category()));
        body.put("subCategory", nullToEmpty(ai.subCategory()));
        body.put("gender", nullToEmpty(ai.gender()));
        body.put("ageGroup", nullToEmpty(ai.ageGroup()));
        body.put("primaryColor", nullToEmpty(ai.primaryColor()));
        body.put("secondaryColors", ai.secondaryColors() == null ? List.of() : ai.secondaryColors());
        body.put("pattern", nullToEmpty(ai.pattern()));
        body.put("material", nullToEmpty(ai.material()));
        body.put("sleeveType", nullToEmpty(ai.sleeveType()));
        body.put("neckType", nullToEmpty(ai.neckType()));
        body.put("collarType", nullToEmpty(ai.collarType()));
        body.put("fit", nullToEmpty(ai.fit()));
        body.put("occasion", nullToEmpty(ai.occasion()));
        body.put("style", nullToEmpty(ai.style()));
        body.put("lengthMeters", nullToEmpty(ai.lengthMeters()));
        body.put("productType", nullToEmpty(ai.productType()));
        body.put("productDescription", nullToEmpty(ai.productDescription()));
        body.put("suggestedTitles", ai.suggestedTitles() == null ? List.of() : ai.suggestedTitles());
        body.put("suggestedDescriptions", ai.suggestedDescriptions() == null ? List.of() : ai.suggestedDescriptions());
        body.put("keywords", ai.keywords() == null ? List.of() : ai.keywords());
        body.put("overallConfidence", ai.overallConfidence() == null ? 0.0 : ai.overallConfidence());
        return ApiResponse.ok(body);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
