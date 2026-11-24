package com.catalogstudio.tips.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.tips.dto.TipVideoCommentCreateRequest;
import com.catalogstudio.tips.dto.TipVideoCommentResponse;
import com.catalogstudio.tips.dto.TipVideoResponse;
import com.catalogstudio.tips.entity.TipVideo;
import com.catalogstudio.tips.service.TipVideoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Video libraries")
public class TipVideoController {

    private final TipVideoService tipVideoService;

    @GetMapping("/api/v1/app-guide")
    public ApiResponse<List<TipVideoResponse>> listAppGuide() {
        return ApiResponse.ok(tipVideoService.listByKind(TipVideo.Kind.APP_GUIDE));
    }

    @GetMapping("/api/v1/tutorials")
    public ApiResponse<List<TipVideoResponse>> listTutorials() {
        return ApiResponse.ok(tipVideoService.listByKind(TipVideo.Kind.ECOMMERCE_TUTORIAL));
    }

    /** Legacy alias for ecommerce tutorials. */
    @GetMapping("/api/v1/tips")
    public ApiResponse<List<TipVideoResponse>> listTipsAlias() {
        return listTutorials();
    }

    @GetMapping("/api/v1/app-guide/{id}/comments")
    public ApiResponse<List<TipVideoCommentResponse>> listAppGuideComments(@PathVariable UUID id) {
        return ApiResponse.ok(tipVideoService.listComments(id, TipVideo.Kind.APP_GUIDE));
    }

    @PostMapping("/api/v1/app-guide/{id}/comments")
    public ApiResponse<TipVideoCommentResponse> addAppGuideComment(
            @PathVariable UUID id,
            @Valid @RequestBody TipVideoCommentCreateRequest request) {
        return ApiResponse.ok("Comment posted", tipVideoService.addComment(id, TipVideo.Kind.APP_GUIDE, request));
    }

    @GetMapping("/api/v1/tutorials/{id}/comments")
    public ApiResponse<List<TipVideoCommentResponse>> listTutorialComments(@PathVariable UUID id) {
        return ApiResponse.ok(tipVideoService.listComments(id, TipVideo.Kind.ECOMMERCE_TUTORIAL));
    }

    @PostMapping("/api/v1/tutorials/{id}/comments")
    public ApiResponse<TipVideoCommentResponse> addTutorialComment(
            @PathVariable UUID id,
            @Valid @RequestBody TipVideoCommentCreateRequest request) {
        return ApiResponse.ok(
                "Comment posted", tipVideoService.addComment(id, TipVideo.Kind.ECOMMERCE_TUTORIAL, request));
    }
}
