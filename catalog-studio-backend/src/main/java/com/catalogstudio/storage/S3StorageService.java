package com.catalogstudio.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "catalogstudio.storage.provider", havingValue = "s3")
public class S3StorageService implements StorageService {

    @Override
    public StoredFile store(MultipartFile file) {
        throw new UnsupportedOperationException("S3 storage is not configured yet. Set catalogstudio.storage.provider=local.");
    }

    @Override
    public void delete(String storageKey) {
        throw new UnsupportedOperationException("S3 storage is not configured yet.");
    }
}
