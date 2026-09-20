package com.catalogstudio.site.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.site.dto.EnquiryRequest;
import com.catalogstudio.site.dto.SitePublicResponse;
import com.catalogstudio.site.dto.SitePublicResponse.EnquiryAck;
import com.catalogstudio.site.service.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/site")
@RequiredArgsConstructor
@Tag(name = "Public site")
public class SiteController {

    private final SiteService siteService;

    @GetMapping
    @Operation(summary = "Public website branding, featured client, promo codes, and support details")
    public ApiResponse<SitePublicResponse> publicSite() {
        return ApiResponse.ok(siteService.getPublicSite());
    }

    @PostMapping("/enquiries")
    @Operation(summary = "Submit a public enquiry")
    public ApiResponse<EnquiryAck> enquire(@Valid @RequestBody EnquiryRequest request) {
        EnquiryAck ack = siteService.submitEnquiry(request);
        return ApiResponse.ok(ack.message(), ack);
    }
}
