package com.catalogstudio.user.service;

import com.catalogstudio.auth.repository.UserSessionRepository;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.shoot.entity.ProductShoot;
import com.catalogstudio.shoot.entity.ProductShootImage;
import com.catalogstudio.shoot.repository.ProductShootRepository;
import com.catalogstudio.storage.StorageService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

    private static final int PURGE_AFTER_DAYS = 15;

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final ProductShootRepository shootRepository;
    private final StorageService storageService;

    @Transactional
    public void deactivateOwnAccount() {
        User user = userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
        if (user.isStaff()) {
            throw ApiException.badRequest("Staff accounts cannot self-deactivate from the dashboard");
        }
        if (user.getStatus() == User.UserStatus.DISABLED && user.getDeactivatedAt() != null) {
            return;
        }
        user.setStatus(User.UserStatus.DISABLED);
        user.setDeactivatedAt(Instant.now());
        sessionRepository.revokeAllForUser(user.getId());
        log.info("User {} requested account deactivation; purge after {} days", user.getEmail(), PURGE_AFTER_DAYS);
    }

    @Transactional
    public int purgeDueAccounts() {
        Instant cutoff = Instant.now().minus(PURGE_AFTER_DAYS, ChronoUnit.DAYS);
        List<User> due = userRepository.findByStatusAndDeactivatedAtBefore(User.UserStatus.DISABLED, cutoff);
        int purged = 0;
        for (User user : new ArrayList<>(due)) {
            try {
                purgeUser(user);
                purged++;
            } catch (RuntimeException ex) {
                log.error("Failed to purge deactivated user {}", user.getEmail(), ex);
            }
        }
        return purged;
    }

    private void purgeUser(User user) {
        Long userId = user.getId();
        String email = user.getEmail();
        List<ProductShoot> shoots = shootRepository.findByUser_Id(userId);
        for (ProductShoot shoot : shoots) {
            if (shoot.getImages() != null) {
                for (ProductShootImage image : shoot.getImages()) {
                    deleteQuietly(image.getStorageKey());
                }
            }
            shootRepository.delete(shoot);
        }
        sessionRepository.deleteByUserId(userId);
        userRepository.delete(user);
        log.info("Purged deactivated account {} (id={}) and related cascaded data", email, userId);
    }

    private void deleteQuietly(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            return;
        }
        try {
            storageService.delete(storageKey);
        } catch (RuntimeException ex) {
            log.warn("Could not delete storage key {}: {}", storageKey, ex.toString());
        }
    }
}
