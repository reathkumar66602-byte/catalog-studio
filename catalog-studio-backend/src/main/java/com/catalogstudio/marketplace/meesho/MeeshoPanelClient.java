package com.catalogstudio.marketplace.meesho;

import java.util.List;
import java.util.Map;

/**
 * Supplier-panel session client (Base.com-style email/password connector).
 * Live Meesho panel routes change; implementations must degrade gracefully.
 * Default methods keep older mocks/tests compiling while new OMS flows opt in.
 */
public interface MeeshoPanelClient {

    AuthResult authenticate(String email, String password);

    List<MeeshoRemoteOrder> fetchPendingOrders(MeeshoPanelSession session);

    int pushStock(MeeshoPanelSession session, List<MeeshoStockUpdate> updates);

    List<MeeshoLabelResult> fetchLabels(MeeshoPanelSession session, List<String> externalOrderIds);

    default List<MeeshoRemoteOffer> fetchOffers(MeeshoPanelSession session) {
        return List.of();
    }

    default int pushPrices(MeeshoPanelSession session, Map<String, Double> skuPrices) {
        return 0;
    }

    default List<MeeshoRemoteReturn> fetchReturns(MeeshoPanelSession session) {
        return List.of();
    }

    default boolean acceptOrder(MeeshoPanelSession session, String externalOrderId) {
        return false;
    }

    default boolean updateOrderStatus(MeeshoPanelSession session, String externalOrderId, String meeshoStatus) {
        return false;
    }

    default MeeshoLabelResult createShipment(MeeshoPanelSession session, String externalOrderId) {
        return new MeeshoLabelResult(externalOrderId, null, null, "PENDING", "Meesho shipment not created");
    }

    record AuthResult(boolean success, boolean verified, MeeshoPanelSession session, String message) {
        public static AuthResult ok(MeeshoPanelSession session, String message) {
            return new AuthResult(true, session.verified(), session, message);
        }

        public static AuthResult failed(String message) {
            return new AuthResult(false, false, null, message);
        }
    }
}
