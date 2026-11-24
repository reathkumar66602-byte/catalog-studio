package com.catalogstudio.wholesale.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.supplier.client.EntitlementClient;
import com.catalogstudio.supplier.client.EntitlementSnapshot;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Wholesale capability checks via billing entitlements from platform-api.
 */
@Service
@RequiredArgsConstructor
public class WholesaleAccessService {

    public static final String SUPPLIER_PLAN = "SUPPLIER";
    public static final String FEATURE_SUPPLIER = "wholesaleSupplier";

    private final EntitlementClient entitlementClient;

    public boolean isLiveStockBuyer(Long userId) {
        return entitlementClient.accessOf(userId).accessEntitled();
    }

    public boolean isSupplier(Long userId) {
        EntitlementSnapshot status = entitlementClient.accessOf(userId);
        if (status.staff()) {
            return true;
        }
        return status.accessEntitled() && hasSupplierCapability(status);
    }

    public void requireLiveStockBuyer(Long userId) {
        if (!isLiveStockBuyer(userId)) {
            throw ApiException.paymentRequired("An active subscription is required to book wholesale stock.");
        }
    }

    public void requireSupplier(Long userId) {
        EntitlementSnapshot status = entitlementClient.accessOf(userId);
        if (status.staff()) {
            return;
        }
        if (!status.accessEntitled()) {
            throw ApiException.paymentRequired("An active subscription is required.");
        }
        if (!hasSupplierCapability(status)) {
            throw ApiException.forbidden("Upgrade to a Supplier plan to open a wholesale store.");
        }
    }

    static boolean hasSupplierCapability(EntitlementSnapshot status) {
        String plan = status.plan() == null ? "" : status.plan().toUpperCase(Locale.ROOT);
        if (SUPPLIER_PLAN.equals(plan) || plan.startsWith("SUPPLIER_")) {
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
