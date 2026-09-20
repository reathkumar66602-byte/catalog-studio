package com.catalogstudio.extension.repository;

import com.catalogstudio.extension.entity.ExtensionInventoryPhoto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtensionInventoryPhotoRepository extends JpaRepository<ExtensionInventoryPhoto, Long> {
    Optional<ExtensionInventoryPhoto> findByUserIdAndSourceId(Long userId, String sourceId);

    long countByUserId(Long userId);
}
