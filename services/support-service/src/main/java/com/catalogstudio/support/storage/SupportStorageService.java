package com.catalogstudio.support.storage;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.support.config.SupportProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
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
public class SupportStorageService {

    private static final Logger log = LoggerFactory.getLogger(SupportStorageService.class);
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".exe", ".bat", ".cmd", ".com", ".msi", ".scr", ".js", ".jar", ".sh", ".ps1", ".dll", ".vbs");

    private final SupportProperties properties;

    public SupportStorageService(SupportProperties properties) {
        this.properties = properties;
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Attachment file is required");
        }
        if (file.getSize() > properties.upload().maxFileBytes()) {
            throw ApiException.badRequest("Attachment exceeds maximum allowed size");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String lower = original.toLowerCase(Locale.ROOT);
        for (String blocked : BLOCKED_EXTENSIONS) {
            if (lower.endsWith(blocked)) {
                throw ApiException.badRequest("Executable or script attachments are not allowed");
            }
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read uploaded attachment");
        }
        String contentType = resolveContentType(file, bytes, original);
        if (!properties.upload().allowedContentTypes().contains(contentType)
                && !"image/jpeg".equals(contentType)) {
            throw ApiException.badRequest("Only JPEG, PNG, WebP, PDF, TXT, and DOCX attachments are allowed");
        }
        String ext = extension(contentType, original);
        String key = UUID.randomUUID() + ext;
        Path dir = storageDir();
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(key), bytes);
        } catch (IOException e) {
            log.warn("Could not write attachment to {}: {}", dir, e.toString());
            throw ApiException.badRequest("Could not store attachment");
        }
        return new StoredFile(key, original, contentType, bytes.length, sha256(bytes));
    }

    public byte[] read(String storedName) {
        if (storedName == null || !storedName.matches("[a-fA-F0-9\\-]{36}\\.[A-Za-z0-9]+")) {
            throw ApiException.notFound("Attachment not found");
        }
        Path path = storageDir().resolve(storedName).normalize();
        if (!path.startsWith(storageDir())) {
            throw ApiException.notFound("Attachment not found");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw ApiException.notFound("Attachment not found");
        }
    }

    public void delete(String storedName) {
        try {
            Files.deleteIfExists(storageDir().resolve(storedName));
        } catch (IOException ignored) {
            // best-effort
        }
    }

    public Path storageDir() {
        return Path.of(properties.storage().localPath()).toAbsolutePath().normalize();
    }

    private String resolveContentType(MultipartFile file, byte[] bytes, String original) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType.contains(";")) {
            contentType = contentType.substring(0, contentType.indexOf(';')).trim();
        }
        if ("image/jpg".equals(contentType) || "image/pjpeg".equals(contentType)) {
            contentType = "image/jpeg";
        }
        if (contentType.isBlank() || "application/octet-stream".equals(contentType)) {
            String sniff = sniff(bytes);
            if (!sniff.isBlank()) {
                contentType = sniff;
            } else if (original.toLowerCase(Locale.ROOT).endsWith(".txt")) {
                contentType = "text/plain";
            } else if (original.toLowerCase(Locale.ROOT).endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (original.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                contentType = "application/pdf";
            }
        }
        return contentType;
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
        if (bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') {
            return "application/pdf";
        }
        return "";
    }

    private String extension(String contentType, String original) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "text/plain" -> ".txt";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            case "image/jpeg" -> ".jpg";
            default -> {
                int dot = original.lastIndexOf('.');
                yield dot > 0 ? original.substring(dot).toLowerCase(Locale.ROOT) : ".bin";
            }
        };
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    public record StoredFile(
            String storedName,
            String originalName,
            String contentType,
            long sizeBytes,
            String checksum
    ) {}
}
