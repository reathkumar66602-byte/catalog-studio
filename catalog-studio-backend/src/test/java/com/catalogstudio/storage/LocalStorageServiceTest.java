package com.catalogstudio.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class LocalStorageServiceTest {

    private final LocalStorageService storage = new LocalStorageService(new CatalogStudioProperties(
            new CatalogStudioProperties.Jwt("secret-secret-secret-secret-secret-12", 15, 7),
            new CatalogStudioProperties.Cors(List.of("http://localhost:5173")),
            new CatalogStudioProperties.Storage("local", "./target/test-uploads", "http://localhost:8080/api/v1/files"),
            new CatalogStudioProperties.Ai("mock", "", "mock", 30, "classpath:prompts/product-analysis.txt", "gpt-image-1"),
            new CatalogStudioProperties.Upload(5, 10_485_760, List.of("image/jpeg", "image/png", "image/webp")),
            new CatalogStudioProperties.Security(60, 10),
            new CatalogStudioProperties.Seed("a@test.local", "x", "s@test.local", "y"),
            new CatalogStudioProperties.Mail(false, "zoho", "smtp.zoho.in", 587, "", "", "", "Catalog Studio", true,
                    "https://api.zeptomail.in/v1.1/email", "", ""),
            new CatalogStudioProperties.Otp(false, 6, 10, 5, 60, true),
            new CatalogStudioProperties.Google(""),
            new CatalogStudioProperties.Billing("919560111849")
    ));

    @Test
    void rejectsInvalidMimeType() {
        MockMultipartFile file = new MockMultipartFile("images", "note.txt", "text/plain", "hello".getBytes());
        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("JPG");
    }

    @Test
    void storesJpegImage() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        MockMultipartFile file = new MockMultipartFile("images", "shirt.jpg", "image/jpeg", jpeg);
        var stored = storage.store(file);
        org.assertj.core.api.Assertions.assertThat(stored.storageKey()).endsWith(".jpg");
        org.assertj.core.api.Assertions.assertThat(stored.publicUrl()).contains("/api/v1/files/");
        storage.delete(stored.storageKey());
    }
}
