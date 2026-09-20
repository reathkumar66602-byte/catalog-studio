package com.catalogstudio.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    StoredFile store(MultipartFile file);

    void delete(String storageKey);

    record StoredFile(String storageKey, String publicUrl, String contentType, long size, byte[] bytes) {}
}
