package com.catalogstudio.tips.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Who may watch ecommerce tutorial videos (paid library).
 * App guides are free for any authenticated user and do not use this helper.
 *
 * <p>Access is limited to staff and entitled {@code TUTORIAL_*} plan holders.
 * Seller / supplier Catalog Studio plans do not include the tutorial library.
 */
@Service
@RequiredArgsConstructor
public class TutorialAccessService {

    public static final String FEATURE_ECOMMERCE_TUTORIALS = "ecommerce_tutorials";
    public static final String FEATURE_ECOMMERCE_TIPS = "ecommerce_tips";
    public static final String PLAN_FEATURE_TUTORIALS = "ecommerceTutorials";

    private final UserRepository userRepository;
    private final SubscriptionAccessService subscriptionAccessService;

    public boolean hasTutorialAccess(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        return hasTutorialAccess(user);
    }

    public boolean hasTutorialAccess(User user) {
        if (user == null) {
            return false;
        }
        if (user.isStaff()) {
            return true;
        }
        SubscriptionStatusResponse status = subscriptionAccessService.statusOf(user);
        if (!status.accessEntitled()) {
            return false;
        }
        String plan = status.plan() == null ? "" : status.plan().toUpperCase(Locale.ROOT);
        return plan.startsWith("TUTORIAL_");
    }

    /** Tutorial SKU is a focused library — not a full Catalog Studio workspace. */
    public boolean isTutorialOnlyPlan(User user) {
        if (user == null || user.isStaff()) {
            return false;
        }
        SubscriptionStatusResponse status = subscriptionAccessService.statusOf(user);
        String plan = status.plan() == null ? "" : status.plan().toUpperCase(Locale.ROOT);
        return plan.startsWith("TUTORIAL_") && status.accessEntitled();
    }

    public void requireTutorialAccess(User user) {
        if (!hasTutorialAccess(user)) {
            throw ApiException.forbidden(
                    "Ecommerce tutorials require a Tutorial subscription. Open Subscription to purchase access.");
        }
    }

    static boolean planFeatureEnabled(Map<String, Object> features, String key) {
        if (features == null || features.isEmpty()) {
            return false;
        }
        Object value = features.get(key);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                return false;
            }
            if ("false".equalsIgnoreCase(trimmed) || "0".equals(trimmed)) {
                return false;
            }
            return true;
        }
        return true;
    }
}
