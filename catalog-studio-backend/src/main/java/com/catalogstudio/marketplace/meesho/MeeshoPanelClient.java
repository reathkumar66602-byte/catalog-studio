package com.catalogstudio.marketplace.meesho;

import java.util.List;

/**
 * Supplier-panel session client (Base.com-style email/password connector).
 * Live Meesho panel routes change; implementations must degrade gracefully.
 */
public interface MeeshoPanelClient {

    AuthResult authenticate(String email, String password);

    List<MeeshoRemoteOrder> fetchPendingOrders(MeeshoPanelSession session);

    int pushStock(MeeshoPanelSession session, List<MeeshoStockUpdate> updates);

    List<MeeshoLabelResult> fetchLabels(MeeshoPanelSession session, List<String> externalOrderIds);

    record AuthResult(boolean success, boolean verified, MeeshoPanelSession session, String message) {
        public static AuthResult ok(MeeshoPanelSession session, String message) {
            return new AuthResult(true, session.verified(), session, message);
        }

        public static AuthResult failed(String message) {
            return new AuthResult(false, false, null, message);
        }
    }
}
