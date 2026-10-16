package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.security.Roles;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Wholesale capability checks. Sellers need the SUPPLIER plan (or features_json.wholesaleSupplier).
 * Staff (ADMIN / SUPERADMIN) may open and manage a store without that plan.
 */
@Service
@RequiredArgsConstructor
public class WholesaleAccessService {

    public static final String SUPPLIER_PLAN = "SUPPLIER";
    public static final String FEATURE_SUPPLIER = "wholesaleSupplier";

    private final SubscriptionAccessService subscriptionAccessService;
    private final UserRepository userRepository;

    public boolean isLiveStockBuyer(Long userId) {
        return subscriptionAccessService.statusOf(userId).accessEntitled();
    }

    public boolean isSupplier(Long userId) {
        if (isStaffUser(userId)) {
            return true;
        }
        SubscriptionStatusResponse status = subscriptionAccessService.statusOf(userId);
        return status.accessEntitled() && hasSupplierCapability(status);
    }

    public void requireLiveStockBuyer(Long userId) {
        if (!isLiveStockBuyer(userId)) {
            throw ApiException.paymentRequired("An active subscription is required to book wholesale stock.");
        }
    }

    public void requireSupplier(Long userId) {
        if (isStaffUser(userId)) {
            return;
        }
        SubscriptionStatusResponse status = subscriptionAccessService.statusOf(userId);
        if (!status.accessEntitled()) {
            throw ApiException.paymentRequired("An active subscription is required.");
        }
        if (!hasSupplierCapability(status)) {
            throw ApiException.forbidden("Upgrade to the SUPPLIER plan to open a wholesale store.");
        }
    }

    private boolean isStaffUser(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        return Roles.isStaff(user);
    }

    static boolean hasSupplierCapability(SubscriptionStatusResponse status) {
        if (SUPPLIER_PLAN.equalsIgnoreCase(status.plan())) {
            return true;
        }
        Map<String, Object> features = status.features();
        if (features == null) {
            return false;
        }
        Object flag = features.get(FEATURE_SUPPLIER);
        return Boolean.TRUE.equals(flag) || (flag instanceof String text && Boolean.parseBoolean(text));
    }
}
