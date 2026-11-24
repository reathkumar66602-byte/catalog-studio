package com.catalogstudio.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    StoredFile store(MultipartFile file);

    StoredFile storeBytes(byte[] bytes, String contentType);

    /** Stores an MP4/WebM/MOV tip video; streams to disk without keeping full bytes in memory. */
    StoredFile storeVideo(MultipartFile file);

    byte[] read(String storageKey);

    void delete(String storageKey);

    record StoredFile(String storageKey, String publicUrl, String contentType, long size, byte[] bytes) {}
}
