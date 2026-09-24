package com.catalogstudio.access.service;

import com.catalogstudio.access.FeatureCatalog;
import com.catalogstudio.access.entity.UserFeatureAccess;
import com.catalogstudio.access.repository.UserFeatureAccessRepository;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.user.entity.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeatureAccessService {

    private final UserFeatureAccessRepository accessRepository;

    @Transactional(readOnly = true)
    public Map<String, Boolean> mapFor(User user) {
        Map<String, Boolean> map = FeatureCatalog.defaultsEnabled();
        if (user == null || user.isStaff()) {
            return map;
        }
        for (UserFeatureAccess row : accessRepository.findByUser_Id(user.getId())) {
            if (FeatureCatalog.KEYS.contains(row.getFeatureKey())) {
                map.put(row.getFeatureKey(), row.isEnabled());
            }
        }
        return map;
    }

    @Transactional(readOnly = true)
    public List<String> enabledKeys(User user) {
        List<String> keys = new ArrayList<>();
        mapFor(user).forEach((key, enabled) -> {
            if (Boolean.TRUE.equals(enabled)) {
                keys.add(key);
            }
        });
        return keys;
    }

    @Transactional(readOnly = true)
    public Map<Long, Map<String, Boolean>> mapForUsers(Collection<User> users) {
        Map<Long, Map<String, Boolean>> result = new HashMap<>();
        if (users == null || users.isEmpty()) {
            return result;
        }
        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, List<UserFeatureAccess>> byUser = new HashMap<>();
        for (UserFeatureAccess row : accessRepository.findByUser_IdIn(ids)) {
            byUser.computeIfAbsent(row.getUser().getId(), ignored -> new ArrayList<>()).add(row);
        }
        for (User user : users) {
            Map<String, Boolean> map = FeatureCatalog.defaultsEnabled();
            if (!user.isStaff()) {
                for (UserFeatureAccess row : byUser.getOrDefault(user.getId(), List.of())) {
                    if (FeatureCatalog.KEYS.contains(row.getFeatureKey())) {
                        map.put(row.getFeatureKey(), row.isEnabled());
                    }
                }
            }
            result.put(user.getId(), map);
        }
        return result;
    }

    @Transactional
    public Map<String, Boolean> replace(User target, Map<String, Boolean> features) {
        if (target == null) {
            throw ApiException.notFound("User not found");
        }
        if (target.isStaff()) {
            throw ApiException.badRequest("Staff accounts always have full workspace access");
        }
        if (features == null || features.isEmpty()) {
            throw ApiException.badRequest("Select at least one feature flag to save");
        }
        Map<String, Boolean> next = new LinkedHashMap<>(mapFor(target));
        for (Map.Entry<String, Boolean> entry : features.entrySet()) {
            if (!FeatureCatalog.KEYS.contains(entry.getKey())) {
                throw ApiException.badRequest("Unknown feature: " + entry.getKey());
            }
            boolean enabled = Boolean.TRUE.equals(entry.getValue()) || FeatureCatalog.ALL.stream()
                    .anyMatch(item -> item.key().equals(entry.getKey()) && item.required());
            next.put(entry.getKey(), enabled);
            UserFeatureAccess row = accessRepository.findByUser_IdAndFeatureKey(target.getId(), entry.getKey())
                    .orElseGet(() -> UserFeatureAccess.builder()
                            .user(target)
                            .featureKey(entry.getKey())
                            .enabled(Boolean.TRUE.equals(entry.getValue()))
                            .build());
            row.setEnabled(enabled);
            accessRepository.save(row);
        }
        return next;
    }
}
