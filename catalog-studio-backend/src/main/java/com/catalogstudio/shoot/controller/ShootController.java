package com.catalogstudio.shoot.controller;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.shoot.dto.ShootResponse;
import com.catalogstudio.shoot.entity.ProductShootImage;
import com.catalogstudio.shoot.service.ShootService;
import com.catalogstudio.storage.StorageService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/shoots")
@RequiredArgsConstructor
public class ShootController {

    private final ShootService shootService;
    private final StorageService storageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ShootResponse> create(
            @RequestParam("mode") String mode,
            @RequestParam("modelAge") String modelAge,
            @RequestParam(value = "angles", required = false) List<String> angles,
            @RequestParam(value = "flipkart", required = false) Boolean flipkart,
            @RequestParam(value = "marketplace", defaultValue = "false") boolean marketplace,
            @RequestParam(value = "meeshoBackground", required = false) String meeshoBackground,
            @RequestParam(value = "front", required = false) MultipartFile front,
            @RequestParam(value = "back", required = false) MultipartFile back,
            @RequestParam(value = "products", required = false) List<MultipartFile> products
    ) {
        ShootResponse data = shootService.create(
                SecurityUtils.currentUserId(),
                mode,
                modelAge,
                angles,
                flipkart == null ? marketplace : Boolean.TRUE.equals(flipkart),
                true,
                meeshoBackground,
                front,
                back,
                products);
        return ApiResponse.ok("Catalog photos generated", data);
    }

    @GetMapping
    public ApiResponse<List<ShootResponse>> recent() {
        return ApiResponse.ok(shootService.recent(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<ShootResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(shootService.get(SecurityUtils.currentUserId(), id));
    }

    @GetMapping("/{id}/images/{imageId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @PathVariable UUID imageId) {
        ProductShootImage image = shootService.downloadable(SecurityUtils.currentUserId(), id, imageId);
        byte[] bytes = storageService.read(image.getStorageKey());
        String filename = filename(image);
        MediaType type = MediaType.parseMediaType(
                image.getContentType() == null ? MediaType.IMAGE_PNG_VALUE : image.getContentType());
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }

    private static String filename(ProductShootImage image) {
        String ext = image.getContentType() != null && image.getContentType().contains("png") ? "png" : "jpg";
        if ("MARKETPLACE".equals(image.getKind())) {
            return "flipkart-amazon-" + image.getSortOrder() + "." + ext;
        }
        if ("MEESHO".equals(image.getKind())) {
            return "meesho-" + image.getSortOrder() + "." + ext;
        }
        return image.getKind().toLowerCase() + "." + ext;
    }
}
