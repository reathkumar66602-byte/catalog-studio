package com.catalogstudio.storage;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "catalogstudio.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/jpg", "image/pjpeg", "image/png", "image/webp");

    private final CatalogStudioProperties properties;

    public LocalStorageService(CatalogStudioProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredFile store(MultipartFile file) {
        validate(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.warn("Could not read uploaded image: {}", e.toString());
            throw ApiException.badRequest("Could not store uploaded image");
        }
        String contentType = resolvedContentType(file, bytes);
        String ext = extension(contentType);
        String key = UUID.randomUUID() + ext;
        Path dir = storageDir();
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(key), bytes);
        } catch (IOException e) {
            log.warn("Could not write uploaded image to {}: {}", dir, e.toString());
            throw ApiException.badRequest("Could not store uploaded image");
        }
        String url = properties.storage().publicBaseUrl().replaceAll("/$", "") + "/" + key;
        return new StoredFile(key, url, contentType, bytes.length, bytes);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(storageDir().resolve(storageKey));
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    public Path storageDir() {
        return Path.of(properties.storage().localPath()).toAbsolutePath().normalize();
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Image file is required");
        }
        if (file.getSize() > properties.upload().maxFileBytes()) {
            throw ApiException.badRequest("Each image must be 10MB or smaller");
        }
    }

    private String resolvedContentType(MultipartFile file, byte[] bytes) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType.contains(";")) {
            contentType = contentType.substring(0, contentType.indexOf(';')).trim();
        }
        if ("image/jpg".equals(contentType) || "image/pjpeg".equals(contentType)) {
            contentType = "image/jpeg";
        }
        if (!ALLOWED.contains(contentType)) {
            contentType = sniff(bytes);
        }
        if (!ALLOWED.contains(contentType) && !properties.upload().allowedContentTypes().contains(contentType)) {
            throw ApiException.badRequest("Only JPG, JPEG, PNG and WEBP images are allowed");
        }
        return "image/jpg".equals(contentType) ? "image/jpeg" : contentType;
    }

    private String sniff(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }
        return "";
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
