package com.catalogstudio.tips.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.storage.StorageService;
import com.catalogstudio.tips.dto.TipVideoResponse;
import com.catalogstudio.tips.dto.TipVideoSettingsResponse;
import com.catalogstudio.tips.dto.TipVideoSettingsUpdateRequest;
import com.catalogstudio.tips.entity.TipVideo;
import com.catalogstudio.tips.entity.TipVideoSettings;
import com.catalogstudio.tips.repository.TipVideoRepository;
import com.catalogstudio.tips.repository.TipVideoSettingsRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class TipVideoService {

    private final TipVideoRepository tipVideoRepository;
    private final TipVideoSettingsRepository settingsRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public TipVideoSettingsResponse settings() {
        TipVideoSettings settings = requireSettings();
        return new TipVideoSettingsResponse(
                settings.getMaxCount(),
                settings.getMaxTotalBytes(),
                tipVideoRepository.sumSizeBytes(),
                tipVideoRepository.count());
    }

    @Transactional
    public TipVideoSettingsResponse updateSettings(TipVideoSettingsUpdateRequest request) {
        User actor = requireStaff();
        if (actor.getRole() != User.Role.SUPERADMIN) {
            throw ApiException.forbidden("Only super admin can change tip video caps");
        }
        TipVideoSettings settings = requireSettings();
        settings.setMaxCount(request.maxCount());
        settings.setMaxTotalBytes(request.maxTotalBytes());
        enforceCapsByDeletingOldest(settings);
        return settings();
    }

    @Transactional(readOnly = true)
    public List<TipVideoResponse> listForWatchers() {
        return tipVideoRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TipVideoResponse> listForAdmin() {
        requireStaff();
        return listForWatchers();
    }

    @Transactional(readOnly = true)
    public TipVideo requireByUuid(UUID id) {
        return tipVideoRepository.findByUuid(id)
                .orElseThrow(() -> ApiException.notFound("Tip video not found"));
    }

    @Transactional
    public TipVideoResponse upload(String title, MultipartFile file) {
        User actor = requireStaff();
        if (!StringUtils.hasText(title)) {
            throw ApiException.badRequest("Title is required");
        }
        String cleanTitle = title.trim();
        if (cleanTitle.length() > 200) {
            throw ApiException.badRequest("Title must be 200 characters or fewer");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Video file is required");
        }

        TipVideoSettings settings = requireSettings();
        long incoming = file.getSize();
        if (incoming > settings.getMaxTotalBytes()) {
            throw ApiException.badRequest(
                    "This video is larger than the total storage cap (" + settings.getMaxTotalBytes() + " bytes)");
        }

        makeRoomForUpload(settings, incoming);

        StorageService.StoredFile stored = storageService.storeVideo(file);
        TipVideo video = tipVideoRepository.save(TipVideo.builder()
                .title(cleanTitle)
                .storageKey(stored.storageKey())
                .publicUrl(stored.publicUrl())
                .contentType(stored.contentType())
                .sizeBytes(stored.size())
                .uploadedBy(actor)
                .build());
        log.info("Tip video {} uploaded by {} ({} bytes)", video.getUuid(), actor.getEmail(), video.getSizeBytes());
        return toResponse(video);
    }

    @Transactional
    public void delete(UUID id) {
        requireStaff();
        TipVideo video = requireByUuid(id);
        tipVideoRepository.delete(video);
        storageService.delete(video.getStorageKey());
    }

    private void makeRoomForUpload(TipVideoSettings settings, long incomingBytes) {
        while (tipVideoRepository.count() >= settings.getMaxCount()) {
            deleteOldest();
        }
        while (tipVideoRepository.sumSizeBytes() + incomingBytes > settings.getMaxTotalBytes()
                && tipVideoRepository.count() > 0) {
            deleteOldest();
        }
        if (tipVideoRepository.sumSizeBytes() + incomingBytes > settings.getMaxTotalBytes()) {
            throw ApiException.badRequest("Not enough tip-video storage even after removing older videos");
        }
    }

    private void enforceCapsByDeletingOldest(TipVideoSettings settings) {
        while (tipVideoRepository.count() > settings.getMaxCount()) {
            deleteOldest();
        }
        while (tipVideoRepository.sumSizeBytes() > settings.getMaxTotalBytes() && tipVideoRepository.count() > 0) {
            deleteOldest();
        }
    }

    private void deleteOldest() {
        TipVideo oldest = tipVideoRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> ApiException.badRequest("No tip video available to remove"));
        tipVideoRepository.delete(oldest);
        storageService.delete(oldest.getStorageKey());
        log.info("FIFO removed tip video {} to free library space", oldest.getUuid());
    }

    private TipVideoSettings requireSettings() {
        return settingsRepository.findBySettingsKey("default")
                .orElseGet(() -> settingsRepository.save(TipVideoSettings.builder().build()));
    }

    private User requireStaff() {
        User actor = userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
        if (!actor.isStaff()) {
            throw ApiException.forbidden("Only admin or super admin can manage tip videos");
        }
        return actor;
    }

    private TipVideoResponse toResponse(TipVideo video) {
        String email = video.getUploadedBy() == null ? null : video.getUploadedBy().getEmail();
        return new TipVideoResponse(
                video.getUuid(),
                video.getTitle(),
                video.getPublicUrl(),
                video.getContentType(),
                video.getSizeBytes(),
                email,
                video.getCreatedAt());
    }
}
