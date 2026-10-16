package com.catalogstudio.tips.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.tips.dto.TipVideoResponse;
import com.catalogstudio.tips.dto.TipVideoSettingsResponse;
import com.catalogstudio.tips.dto.TipVideoSettingsUpdateRequest;
import com.catalogstudio.tips.service.TipVideoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/tips")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin tip videos")
public class TipVideoAdminController {

    private final TipVideoService tipVideoService;

    @GetMapping
    public ApiResponse<List<TipVideoResponse>> list() {
        return ApiResponse.ok(tipVideoService.listForAdmin());
    }

    @GetMapping("/settings")
    public ApiResponse<TipVideoSettingsResponse> settings() {
        return ApiResponse.ok(tipVideoService.settings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ApiResponse<TipVideoSettingsResponse> updateSettings(
            @Valid @RequestBody TipVideoSettingsUpdateRequest request) {
        return ApiResponse.ok("Tip video caps updated", tipVideoService.updateSettings(request));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TipVideoResponse> upload(
            @RequestParam("title") String title,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok("Tip video uploaded", tipVideoService.upload(title, file));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        tipVideoService.delete(id);
        return ApiResponse.okMessage("Tip video deleted");
    }
}
