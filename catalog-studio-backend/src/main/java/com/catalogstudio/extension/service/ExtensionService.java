package com.catalogstudio.extension.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.common.util.TokenHasher;
import com.catalogstudio.extension.dto.ExtensionActivityRequest;
import com.catalogstudio.extension.entity.ExtensionActivityLog;
import com.catalogstudio.extension.entity.ExtensionDevice;
import com.catalogstudio.extension.repository.ExtensionActivityLogRepository;
import com.catalogstudio.extension.repository.ExtensionDeviceRepository;
import com.catalogstudio.product.dto.ProductResponse;
import com.catalogstudio.product.dto.ProfileResponse;
import com.catalogstudio.product.entity.AutofillProfile;
import com.catalogstudio.product.repository.AutofillProfileRepository;
import com.catalogstudio.business.entity.Business;
import com.catalogstudio.business.repository.BusinessRepository;
import com.catalogstudio.product.repository.ProductRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExtensionService {

    private final ExtensionDeviceRepository deviceRepository;
    private final ExtensionActivityLogRepository activityLogRepository;
    private final ProductRepository productRepository;
    private final AutofillProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;

    @Transactional
    public Map<String, Object> generateKey(Long userId, String deviceName) {
        String rawKey = "cst_" + TokenHasher.randomToken(32);
        ExtensionDevice device = deviceRepository.save(ExtensionDevice.builder()
                .user(userRepository.getReferenceById(userId))
                .deviceName(deviceName == null || deviceName.isBlank() ? "Chrome" : deviceName)
                .extensionKeyHash(TokenHasher.sha256(rawKey))
                .status(ExtensionDevice.DeviceStatus.ACTIVE)
                .lastActiveAt(Instant.now())
                .build());
        return Map.of(
                "deviceId", device.getUuid(),
                "deviceName", device.getDeviceName(),
                "pairingKey", rawKey,
                "warning", "This key is shown only once. Store it in the extension."
        );
    }

    @Transactional
    public Map<String, Object> autoPair(Long userId, String existingKey, String deviceName) {
        if (existingKey != null && !existingKey.isBlank()) {
            try {
                ExtensionDevice current = requireActive(existingKey);
                if (current.getUser().getId().equals(userId)) {
                    Map<String, Object> payload = pair(existingKey, deviceName);
                    payload.put("alreadyPaired", true);
                    return payload;
                }
            } catch (ApiException ignored) {
                // stale key — issue a fresh one
            }
        }
        String label = deviceName == null || deviceName.isBlank() ? "Chrome Auto" : deviceName.trim();
        for (ExtensionDevice old : deviceRepository.findByUserIdAndDeviceNameAndStatus(
                userId, label, ExtensionDevice.DeviceStatus.ACTIVE)) {
            old.setStatus(ExtensionDevice.DeviceStatus.REVOKED);
        }
        Map<String, Object> generated = generateKey(userId, label);
        String pairingKey = String.valueOf(generated.get("pairingKey"));
        Map<String, Object> payload = pair(pairingKey, label);
        payload.put("pairingKey", pairingKey);
        payload.put("alreadyPaired", false);
        return payload;
    }

    @Transactional
    public Map<String, Object> pair(String pairingKey, String deviceName) {
        ExtensionDevice device = requireActive(pairingKey);
        if (deviceName != null && !deviceName.isBlank()) {
            device.setDeviceName(deviceName);
        }
        device.setLastActiveAt(Instant.now());
        User user = device.getUser();
        var business = businessRepository.findByUserId(user.getId());
        String workspace = business
                .map(Business::getBusinessName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(user.getName());
        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("id", user.getUuid());
        userPayload.put("name", user.getName());
        userPayload.put("email", user.getEmail());
        userPayload.put("workspace", workspace);
        Map<String, Object> businessPayload = new LinkedHashMap<>();
        businessPayload.put("name", workspace);
        businessPayload.put("address", business.map(Business::getAddress).orElse(""));
        businessPayload.put("gstNumber", business.map(Business::getGstNumber).orElse(""));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceId", device.getUuid());
        result.put("user", userPayload);
        result.put("business", businessPayload);
        result.put("status", "ACTIVE");
        return result;
    }

    @Transactional
    public void unpair(String pairingKey) {
        ExtensionDevice device = requireActive(pairingKey);
        device.setStatus(ExtensionDevice.DeviceStatus.REVOKED);
    }

    @Transactional
    public void revoke(Long userId, UUID deviceId) {
        ExtensionDevice device = deviceRepository.findByUuidAndUserId(deviceId, userId)
                .orElseThrow(() -> ApiException.notFound("Device not found"));
        device.setStatus(ExtensionDevice.DeviceStatus.REVOKED);
    }

    public List<Map<String, Object>> devices(Long userId) {
        return deviceRepository.findByUserId(userId).stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getUuid(),
                        "deviceName", d.getDeviceName(),
                        "status", d.getStatus().name(),
                        "lastActiveAt", d.getLastActiveAt() == null ? "" : d.getLastActiveAt().toString(),
                        "createdAt", d.getCreatedAt().toString()
                ))
                .toList();
    }

    @Transactional
    public void heartbeat(String pairingKey) {
        ExtensionDevice device = requireActive(pairingKey);
        device.setLastActiveAt(Instant.now());
    }

    @Transactional
    public void activity(String pairingKey, ExtensionActivityRequest request) {
        ExtensionDevice device = requireActive(pairingKey);
        activityLogRepository.save(ExtensionActivityLog.builder()
                .user(device.getUser())
                .marketplace(request.marketplace())
                .action(request.action())
                .product(request.productId() == null ? null : productRepository.findByUuid(request.productId()).orElse(null))
                .details(request.details())
                .build());
        device.setLastActiveAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> products(String pairingKey, String query) {
        ExtensionDevice device = requireActive(pairingKey);
        Long userId = device.getUser().getId();
        PageRequest page = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (query == null || query.isBlank()) {
            return productRepository.findByUserId(userId, page).map(ProductResponse::summary).getContent();
        }
        String like = "%" + query.toLowerCase() + "%";
        return productRepository.findAll((root, q, cb) -> cb.and(
                cb.equal(root.get("user").get("id"), userId),
                cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("productType")), like)
                )
        ), page).map(ProductResponse::summary).getContent();
    }

    @Transactional(readOnly = true)
    public ProductResponse product(String pairingKey, UUID id) {
        ExtensionDevice device = requireActive(pairingKey);
        return productRepository.findByUuidAndUserId(id, device.getUser().getId())
                .map(ProductResponse::from)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    public List<ProfileResponse> profiles(String pairingKey) {
        ExtensionDevice device = requireActive(pairingKey);
        return profileRepository.findByUserId(device.getUser().getId()).stream()
                .map(this::toProfile)
                .toList();
    }

    private ProfileResponse toProfile(AutofillProfile p) {
        return new ProfileResponse(p.getUuid(), p.getName(), p.getMarketplace(), p.getProfileJson(),
                p.getCreatedAt(), p.getUpdatedAt());
    }

    public Long requireUserId(String pairingKey) {
        return requireActive(pairingKey).getUser().getId();
    }

    private ExtensionDevice requireActive(String pairingKey) {
        if (pairingKey == null || pairingKey.isBlank()) {
            throw ApiException.unauthorized("Extension key is required");
        }
        ExtensionDevice device = deviceRepository.findByExtensionKeyHash(TokenHasher.sha256(pairingKey))
                .orElseThrow(() -> ApiException.unauthorized("Invalid extension key"));
        if (device.getStatus() != ExtensionDevice.DeviceStatus.ACTIVE) {
            throw ApiException.unauthorized("Extension key has been revoked");
        }
        return device;
    }
}
