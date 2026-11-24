package com.catalogstudio.storage;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves uploaded/generated files under {@code /api/v1/files/**}.
 * PDFs always download. Images may use {@code ?download=1}. Videos are always inline
 * (download attachments are refused so tip/tutorial clips stay stream-only).
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class PublicFileController {

    private final StorageService storageService;

    @GetMapping("/{fileName}")
    public ResponseEntity<byte[]> file(
            @PathVariable String fileName,
            @RequestParam(value = "download", required = false) String downloadFlag) {
        byte[] bytes = storageService.read(fileName);
        MediaType mediaType = mediaTypeFor(fileName);
        boolean video = isVideo(fileName);
        boolean wantDownload = "1".equals(downloadFlag) || "true".equalsIgnoreCase(downloadFlag);
        boolean forceDownload = !video
                && (wantDownload || fileName.toLowerCase(Locale.ROOT).endsWith(".pdf"));
        String disposition = (forceDownload ? "attachment" : "inline") + "; filename=\"" + fileName + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(mediaType)
                .contentLength(bytes.length)
                .body(bytes);
    }

    private static boolean isVideo(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov");
    }

    private static MediaType mediaTypeFor(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        if (lower.endsWith(".mp4")) {
            return MediaType.valueOf("video/mp4");
        }
        if (lower.endsWith(".webm")) {
            return MediaType.valueOf("video/webm");
        }
        if (lower.endsWith(".mov")) {
            return MediaType.valueOf("video/quicktime");
        }
        return MediaType.IMAGE_JPEG;
    }
}
