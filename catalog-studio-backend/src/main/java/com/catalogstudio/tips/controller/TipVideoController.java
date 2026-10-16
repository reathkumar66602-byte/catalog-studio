package com.catalogstudio.tips.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.tips.dto.TipVideoResponse;
import com.catalogstudio.tips.service.TipVideoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tips")
@RequiredArgsConstructor
@Tag(name = "E-commerce Tips")
public class TipVideoController {

    private final TipVideoService tipVideoService;

    @GetMapping
    public ApiResponse<List<TipVideoResponse>> list() {
        return ApiResponse.ok(tipVideoService.listForWatchers());
    }
}
