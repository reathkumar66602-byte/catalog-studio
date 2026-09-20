package com.catalogstudio.product.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.product.dto.ProfileRequest;
import com.catalogstudio.product.dto.ProfileResponse;
import com.catalogstudio.product.dto.TemplateRequest;
import com.catalogstudio.product.dto.TemplateResponse;
import com.catalogstudio.product.service.TemplateProfileService;
import com.catalogstudio.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Templates & Profiles")
public class TemplateProfileController {

    private final TemplateProfileService service;

    @GetMapping("/templates")
    public ApiResponse<Page<TemplateResponse>> templates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.templates(SecurityUtils.currentUserId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TemplateResponse> createTemplate(@Valid @RequestBody TemplateRequest request) {
        return ApiResponse.ok("Template created", service.createTemplate(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/templates/{id}")
    public ApiResponse<TemplateResponse> updateTemplate(@PathVariable UUID id, @Valid @RequestBody TemplateRequest request) {
        return ApiResponse.ok("Template updated", service.updateTemplate(SecurityUtils.currentUserId(), id, request));
    }

    @PostMapping("/templates/{id}/duplicate")
    public ApiResponse<TemplateResponse> duplicateTemplate(@PathVariable UUID id) {
        return ApiResponse.ok("Template duplicated", service.duplicateTemplate(SecurityUtils.currentUserId(), id));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable UUID id) {
        service.deleteTemplate(SecurityUtils.currentUserId(), id);
        return ApiResponse.okMessage("Template deleted");
    }

    @GetMapping("/profiles")
    public ApiResponse<Page<ProfileResponse>> profiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(service.profiles(SecurityUtils.currentUserId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProfileResponse> createProfile(@Valid @RequestBody ProfileRequest request) {
        return ApiResponse.ok("Profile created", service.createProfile(SecurityUtils.currentUserId(), request));
    }

    @PutMapping("/profiles/{id}")
    public ApiResponse<ProfileResponse> updateProfile(@PathVariable UUID id, @Valid @RequestBody ProfileRequest request) {
        return ApiResponse.ok("Profile updated", service.updateProfile(SecurityUtils.currentUserId(), id, request));
    }

    @DeleteMapping("/profiles/{id}")
    public ApiResponse<Void> deleteProfile(@PathVariable UUID id) {
        service.deleteProfile(SecurityUtils.currentUserId(), id);
        return ApiResponse.okMessage("Profile deleted");
    }
}
