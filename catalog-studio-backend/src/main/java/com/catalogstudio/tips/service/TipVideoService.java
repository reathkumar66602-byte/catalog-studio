package com.catalogstudio.tips.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.storage.StorageService;
import com.catalogstudio.tips.dto.TipVideoCommentCreateRequest;
import com.catalogstudio.tips.dto.TipVideoCommentInboxItem;
import com.catalogstudio.tips.dto.TipVideoCommentReplyRequest;
import com.catalogstudio.tips.dto.TipVideoCommentResponse;
import com.catalogstudio.tips.dto.TipVideoResponse;
import com.catalogstudio.tips.dto.TipVideoSettingsResponse;
import com.catalogstudio.tips.dto.TipVideoSettingsUpdateRequest;
import com.catalogstudio.tips.entity.TipVideo;
import com.catalogstudio.tips.entity.TipVideoComment;
import com.catalogstudio.tips.entity.TipVideoSettings;
import com.catalogstudio.tips.repository.TipVideoCommentRepository;
import com.catalogstudio.tips.repository.TipVideoRepository;
import com.catalogstudio.tips.repository.TipVideoSettingsRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
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
    private final TipVideoCommentRepository commentRepository;
    private final TipVideoSettingsRepository settingsRepository;
    private final StorageService storageService;
    private final UserRepository userRepository;
    private final TutorialAccessService tutorialAccessService;

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
    public List<TipVideoResponse> listByKind(TipVideo.Kind kind) {
        User watcher = requireUser();
        TipVideo.Kind resolved = kind == null ? TipVideo.Kind.ECOMMERCE_TUTORIAL : kind;
        if (resolved == TipVideo.Kind.ECOMMERCE_TUTORIAL) {
            tutorialAccessService.requireTutorialAccess(watcher);
        }
        return tipVideoRepository.findByKindOrderByCreatedAtDesc(resolved).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TipVideoResponse> listForWatchers() {
        return listByKind(TipVideo.Kind.ECOMMERCE_TUTORIAL);
    }

    @Transactional(readOnly = true)
    public List<TipVideoResponse> listForAdmin() {
        requireStaff();
        return tipVideoRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TipVideo requireByUuid(UUID id) {
        return tipVideoRepository.findByUuid(id)
                .orElseThrow(() -> ApiException.notFound("Tip video not found"));
    }

    @Transactional
    public TipVideoResponse upload(
            String title, MultipartFile file, String kindRaw, String languageRaw, String seriesKeyRaw) {
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
        TipVideo.Kind kind = parseKind(kindRaw);
        TipVideo.Language language = parseLanguage(languageRaw);
        UUID seriesKey = resolveSeriesKey(seriesKeyRaw, kind);

        tipVideoRepository.findBySeriesKeyAndLanguage(seriesKey, language).ifPresent(existing -> {
            throw ApiException.badRequest(
                    "This lesson already has a " + language.name() + " video. Delete it first or pick another language.");
        });

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
                .kind(kind)
                .language(language)
                .seriesKey(seriesKey)
                .storageKey(stored.storageKey())
                .publicUrl(stored.publicUrl())
                .contentType(stored.contentType())
                .sizeBytes(stored.size())
                .uploadedBy(actor)
                .build());
        log.info(
                "Tip video {} ({}/{}) uploaded by {} ({} bytes)",
                video.getUuid(),
                kind,
                language,
                actor.getEmail(),
                video.getSizeBytes());
        return toResponse(video);
    }

    @Transactional
    public TipVideoResponse upload(String title, MultipartFile file, String kindRaw) {
        return upload(title, file, kindRaw, TipVideo.Language.EN.name(), null);
    }

    @Transactional
    public TipVideoResponse upload(String title, MultipartFile file) {
        return upload(title, file, TipVideo.Kind.ECOMMERCE_TUTORIAL.name());
    }

    @Transactional
    public void delete(UUID id) {
        requireStaff();
        TipVideo video = requireByUuid(id);
        tipVideoRepository.delete(video);
        storageService.delete(video.getStorageKey());
    }

    @Transactional(readOnly = true)
    public List<TipVideoCommentResponse> listComments(UUID videoId, TipVideo.Kind expectedKind) {
        User watcher = requireUser();
        TipVideo video = requireByUuid(videoId);
        assertKind(video, expectedKind);
        assertCanWatch(watcher, video);
        return commentRepository.findByTipVideo_IdOrderByCreatedAtAsc(video.getId()).stream()
                .map(this::toCommentResponse)
                .toList();
    }

    @Transactional
    public TipVideoCommentResponse addComment(
            UUID videoId, TipVideo.Kind expectedKind, TipVideoCommentCreateRequest request) {
        User actor = requireUser();
        TipVideo video = requireByUuid(videoId);
        assertKind(video, expectedKind);
        assertCanWatch(actor, video);
        String body = normalizeBody(request == null ? null : request.body());
        TipVideoComment parent = null;
        if (request != null && request.parentId() != null) {
            parent = commentRepository.findByUuid(request.parentId())
                    .orElseThrow(() -> ApiException.notFound("Parent comment not found"));
            if (!parent.getTipVideo().getId().equals(video.getId())) {
                throw ApiException.badRequest("Parent comment belongs to a different video");
            }
        }
        TipVideoComment saved = commentRepository.save(TipVideoComment.builder()
                .tipVideo(video)
                .user(actor)
                .parent(parent)
                .body(body)
                .build());
        return toCommentResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TipVideoCommentInboxItem> commentInbox() {
        requireStaff();
        return commentRepository.findInboxRoots().stream()
                .map(c -> new TipVideoCommentInboxItem(
                        c.getUuid(),
                        c.getTipVideo().getUuid(),
                        c.getTipVideo().getTitle(),
                        c.getUser() == null ? "Unknown" : c.getUser().getName(),
                        c.getBody(),
                        c.getCreatedAt()))
                .toList();
    }

    @Transactional
    public TipVideoCommentResponse replyToComment(UUID commentId, TipVideoCommentReplyRequest request) {
        User actor = requireStaff();
        TipVideoComment parent = commentRepository.findByUuid(commentId)
                .orElseThrow(() -> ApiException.notFound("Comment not found"));
        String body = normalizeBody(request == null ? null : request.body());
        TipVideoComment saved = commentRepository.save(TipVideoComment.builder()
                .tipVideo(parent.getTipVideo())
                .user(actor)
                .parent(parent)
                .body(body)
                .build());
        return toCommentResponse(saved);
    }

    private void assertCanWatch(User user, TipVideo video) {
        if (video.getKind() == TipVideo.Kind.APP_GUIDE) {
            return;
        }
        tutorialAccessService.requireTutorialAccess(user);
    }

    private void assertKind(TipVideo video, TipVideo.Kind expectedKind) {
        if (expectedKind != null && video.getKind() != expectedKind) {
            throw ApiException.notFound("Tip video not found");
        }
    }

    private String normalizeBody(String body) {
        if (!StringUtils.hasText(body)) {
            throw ApiException.badRequest("Comment is required");
        }
        String clean = body.trim();
        if (clean.length() > 4000) {
            throw ApiException.badRequest("Comment must be 4000 characters or fewer");
        }
        return clean;
    }

    private TipVideo.Kind parseKind(String kindRaw) {
        if (!StringUtils.hasText(kindRaw)) {
            return TipVideo.Kind.ECOMMERCE_TUTORIAL;
        }
        try {
            return TipVideo.Kind.valueOf(kindRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("kind must be APP_GUIDE or ECOMMERCE_TUTORIAL");
        }
    }

    private TipVideo.Language parseLanguage(String languageRaw) {
        if (!StringUtils.hasText(languageRaw)) {
            return TipVideo.Language.EN;
        }
        try {
            return TipVideo.Language.valueOf(languageRaw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("language must be EN, HI, or BN");
        }
    }

    private UUID resolveSeriesKey(String seriesKeyRaw, TipVideo.Kind kind) {
        if (!StringUtils.hasText(seriesKeyRaw)) {
            return UUID.randomUUID();
        }
        UUID seriesKey;
        try {
            seriesKey = UUID.fromString(seriesKeyRaw.trim());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("seriesKey must be a valid UUID");
        }
        TipVideo existing = tipVideoRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(v -> seriesKey.equals(v.getSeriesKey()))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Unknown seriesKey — upload a new lesson first"));
        if (existing.getKind() != kind) {
            throw ApiException.badRequest("seriesKey belongs to a different library (app guide vs tutorials)");
        }
        return seriesKey;
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
        User actor = requireUser();
        if (!actor.isStaff()) {
            throw ApiException.forbidden("Only admin or super admin can manage tip videos");
        }
        return actor;
    }

    private User requireUser() {
        return userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
    }

    private TipVideoResponse toResponse(TipVideo video) {
        String email = video.getUploadedBy() == null ? null : video.getUploadedBy().getEmail();
        TipVideo.Kind kind = video.getKind() == null ? TipVideo.Kind.ECOMMERCE_TUTORIAL : video.getKind();
        TipVideo.Language language = video.getLanguage() == null ? TipVideo.Language.EN : video.getLanguage();
        UUID seriesKey = video.getSeriesKey() == null ? video.getUuid() : video.getSeriesKey();
        String downloadUrl = downloadUrlFor(video);
        return new TipVideoResponse(
                video.getUuid(),
                video.getTitle(),
                kind.name(),
                language.name(),
                seriesKey,
                video.getPublicUrl(),
                downloadUrl,
                video.getContentType(),
                video.getSizeBytes(),
                email,
                video.getCreatedAt());
    }

    private String downloadUrlFor(TipVideo video) {
        String publicUrl = video.getPublicUrl();
        if (!StringUtils.hasText(publicUrl)) {
            return publicUrl;
        }
        return publicUrl.contains("?") ? publicUrl + "&download=1" : publicUrl + "?download=1";
    }

    private TipVideoCommentResponse toCommentResponse(TipVideoComment comment) {
        User author = comment.getUser();
        return new TipVideoCommentResponse(
                comment.getUuid(),
                comment.getTipVideo().getUuid(),
                comment.getParent() == null ? null : comment.getParent().getUuid(),
                comment.getBody(),
                author == null ? "Unknown" : author.getName(),
                author != null && author.isStaff(),
                comment.getCreatedAt());
    }
}
