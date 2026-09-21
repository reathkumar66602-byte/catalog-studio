package com.catalogstudio.extension.controller;



import com.catalogstudio.analysis.dto.AnalysisResultResponse;

import com.catalogstudio.analysis.service.ProductAnalyzeService;

import com.catalogstudio.common.api.ApiResponse;

import com.catalogstudio.common.exception.ApiException;

import com.catalogstudio.extension.dto.CompetitorAnalyzeRequest;

import com.catalogstudio.extension.dto.ExtensionActivityRequest;

import com.catalogstudio.extension.dto.FillGapsRequest;

import com.catalogstudio.extension.dto.PairRequest;

import com.catalogstudio.extension.dto.VerifyShopRequest;

import com.catalogstudio.extension.service.ExtensionFeatureService;

import com.catalogstudio.extension.service.ExtensionService;

import com.catalogstudio.product.dto.ProductResponse;

import com.catalogstudio.product.dto.ProfileResponse;

import com.catalogstudio.security.SecurityUtils;

import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import java.util.List;

import java.util.Map;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.PutMapping;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestHeader;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.multipart.MultipartFile;



@RestController

@RequestMapping("/api/v1/extension")

@RequiredArgsConstructor

@Tag(name = "Chrome Extension")

public class ExtensionController {



    private final ExtensionService extensionService;

    private final ExtensionFeatureService featureService;

    private final ProductAnalyzeService productAnalyzeService;



    @PostMapping("/keys")

    public ApiResponse<Map<String, Object>> generate(@RequestParam(required = false) String deviceName) {

        return ApiResponse.ok("Pairing key generated",

                extensionService.generateKey(SecurityUtils.currentUserId(), deviceName));

    }



    @GetMapping("/devices")

    public ApiResponse<List<Map<String, Object>>> devices() {

        return ApiResponse.ok(extensionService.devices(SecurityUtils.currentUserId()));

    }



    @PostMapping("/devices/{id}/revoke")

    public ApiResponse<Void> revoke(@PathVariable UUID id) {

        extensionService.revoke(SecurityUtils.currentUserId(), id);

        return ApiResponse.okMessage("Device revoked");

    }



    @GetMapping("/workspace")

    public ApiResponse<Map<String, Object>> workspace() {

        return ApiResponse.ok(featureService.workspaceForUser(SecurityUtils.currentUserId()));

    }



    @PutMapping("/workspace/settings")

    public ApiResponse<Map<String, Object>> workspaceSettings(@RequestBody(required = false) Map<String, Object> body) {

        return ApiResponse.ok(featureService.saveSettingsForUser(SecurityUtils.currentUserId(), body));

    }



    @PostMapping("/pair")

    public ApiResponse<Map<String, Object>> pair(

            @RequestBody(required = false) PairRequest request,

            @RequestHeader(value = "X-Extension-Key", required = false) String headerKey

    ) {

        String pairingKey = firstNonBlank(request == null ? null : request.pairingKey(), headerKey);

        if (pairingKey == null) {

            throw ApiException.badRequest("Pairing key is required. Generate one in the dashboard and paste it into the extension.");

        }

        String deviceName = request == null ? "Chrome" : request.deviceName();

        return ApiResponse.ok("Extension paired", extensionService.pair(pairingKey, deviceName));

    }



    @PostMapping("/auto-pair")

    public ApiResponse<Map<String, Object>> autoPair(

            @RequestBody(required = false) PairRequest request,

            @RequestHeader(value = "X-Extension-Key", required = false) String headerKey

    ) {

        Long userId = SecurityUtils.currentUserId();

        String deviceName = request == null ? "Chrome Auto" : request.deviceName();

        return ApiResponse.ok("Extension paired", extensionService.autoPair(userId, headerKey, deviceName));

    }



    @PostMapping("/unpair")

    public ApiResponse<Void> unpair(

            @RequestBody(required = false) PairRequest request,

            @RequestHeader(value = "X-Extension-Key", required = false) String headerKey

    ) {

        String pairingKey = firstNonBlank(request == null ? null : request.pairingKey(), headerKey);

        if (pairingKey == null) {

            throw ApiException.badRequest("Pairing key is required");

        }

        extensionService.unpair(pairingKey);

        return ApiResponse.okMessage("Extension unpaired");

    }



    @GetMapping("/products")

    public ApiResponse<List<ProductResponse>> products(

            @RequestHeader("X-Extension-Key") String key,

            @RequestParam(required = false) String q

    ) {

        return ApiResponse.ok(extensionService.products(key, q));

    }



    @GetMapping("/products/{id}")

    public ApiResponse<ProductResponse> product(

            @RequestHeader("X-Extension-Key") String key,

            @PathVariable UUID id

    ) {

        return ApiResponse.ok(extensionService.product(key, id));

    }



    @GetMapping("/profiles")

    public ApiResponse<List<ProfileResponse>> profiles(@RequestHeader("X-Extension-Key") String key) {

        return ApiResponse.ok(extensionService.profiles(key));

    }



    @PostMapping("/activity")

    public ApiResponse<Void> activity(

            @RequestHeader("X-Extension-Key") String key,

            @Valid @RequestBody ExtensionActivityRequest request

    ) {

        extensionService.activity(key, request);

        return ApiResponse.okMessage("Activity recorded");

    }



    @PostMapping("/heartbeat")

    public ApiResponse<Void> heartbeat(@RequestHeader("X-Extension-Key") String key) {

        extensionService.heartbeat(key);

        return ApiResponse.okMessage("OK");

    }



    @GetMapping("/ping")

    public ApiResponse<Map<String, Object>> ping(@RequestHeader("X-Extension-Key") String key) {

        return ApiResponse.ok(featureService.ping(key));

    }



    @PostMapping("/verify-shop")

    public ApiResponse<Map<String, Object>> verifyShop(

            @RequestHeader("X-Extension-Key") String key,

            @RequestBody(required = false) VerifyShopRequest request

    ) {

        return ApiResponse.ok(featureService.verifyShop(key, request));

    }



    @GetMapping("/settings")

    public ApiResponse<Map<String, Object>> settings(@RequestHeader("X-Extension-Key") String key) {

        return ApiResponse.ok(featureService.settings(key));

    }



    @PutMapping("/settings")

    public ApiResponse<Map<String, Object>> saveSettings(

            @RequestHeader("X-Extension-Key") String key,

            @RequestBody(required = false) Map<String, Object> body

    ) {

        return ApiResponse.ok(featureService.saveSettings(key, body));

    }



    @GetMapping("/categories")

    public ApiResponse<List<Map<String, Object>>> categories(@RequestHeader("X-Extension-Key") String key) {

        return ApiResponse.ok(featureService.categories(key));

    }



    @PostMapping("/match-category")

    public ApiResponse<Map<String, Object>> matchCategory(

            @RequestHeader("X-Extension-Key") String key,

            @RequestBody(required = false) Map<String, String> body

    ) {

        String path = body == null ? "" : body.getOrDefault("path", "");

        return ApiResponse.ok(featureService.matchCategory(key, path));

    }



    @PostMapping("/fill-gaps")

    public ApiResponse<Map<String, Object>> fillGaps(

            @RequestHeader("X-Extension-Key") String key,

            @RequestBody(required = false) FillGapsRequest request

    ) {

        return ApiResponse.ok(featureService.fillGaps(key, request));

    }



    @PostMapping("/competitor-analyze")

    public ApiResponse<Map<String, Object>> competitorAnalyze(

            @RequestHeader("X-Extension-Key") String key,

            @RequestBody(required = false) CompetitorAnalyzeRequest request

    ) {

        return ApiResponse.ok(featureService.competitorAnalyze(key, request));

    }



    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)

    public ApiResponse<Map<String, Object>> photos(

            @RequestHeader("X-Extension-Key") String key,

            @RequestParam("sourceId") String sourceId,

            @RequestParam("thumb") MultipartFile thumb

    ) {

        return ApiResponse.ok(featureService.pushPhoto(key, sourceId, thumb));

    }



    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)

    public ApiResponse<AnalysisResultResponse> analyze(

            @RequestHeader("X-Extension-Key") String key,

            @RequestParam("images") List<MultipartFile> images,

            @RequestParam(value = "primaryIndex", required = false) Integer primaryIndex,

            @RequestParam(value = "marketplace", required = false) String marketplace,

            @RequestParam(value = "categoryHint", required = false) String categoryHint,

            @RequestParam(value = "productTypeHint", required = false) String productTypeHint,

            @RequestParam(value = "meeshoName", required = false) String meeshoName,

            @RequestParam(value = "meeshoUid", required = false) String meeshoUid

    ) {

        Long userId = extensionService.requireUserId(key);

        featureService.assertShopAllowed(key, meeshoName, meeshoUid);

        return ApiResponse.ok("Product analyzed successfully",

                productAnalyzeService.analyze(userId, images, primaryIndex, marketplace, categoryHint, productTypeHint));

    }



    private static String firstNonBlank(String... values) {

        if (values == null) {

            return null;

        }

        for (String value : values) {

            if (value != null && !value.isBlank()) {

                return value.trim();

            }

        }

        return null;

    }

}

