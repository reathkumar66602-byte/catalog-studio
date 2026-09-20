package com.catalogstudio.extension.repository;

import com.catalogstudio.extension.entity.ExtensionDevice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtensionDeviceRepository extends JpaRepository<ExtensionDevice, Long> {
    Optional<ExtensionDevice> findByExtensionKeyHash(String extensionKeyHash);
    List<ExtensionDevice> findByUserId(Long userId);
    Optional<ExtensionDevice> findByUuidAndUserId(UUID uuid, Long userId);
    long countByUserIdAndStatus(Long userId, ExtensionDevice.DeviceStatus status);

    List<ExtensionDevice> findByUserIdAndDeviceNameAndStatus(
            Long userId, String deviceName, ExtensionDevice.DeviceStatus status);
}
