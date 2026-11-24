package com.catalogstudio.events;

public final class CatalogStudioExchanges {

    public static final String DOMAIN_EVENTS = "catalogstudio.domain";

    public static final String ROUTING_SUPPLIER_STORE_CREATED = "supplier.store.created";
    public static final String ROUTING_WHOLESALE_ORDER_PLACED = "wholesale.order.placed";
    public static final String ROUTING_WHOLESALE_ORDER_ACCEPTED = "wholesale.order.accepted";
    public static final String ROUTING_WHOLESALE_ORDER_REJECTED = "wholesale.order.rejected";
    public static final String ROUTING_WHOLESALE_ORDER_FULFILLED = "wholesale.order.fulfilled";

    public static final String QUEUE_PLATFORM_SUPPLIER_NOTIFICATIONS = "platform.supplier.notifications";

    private CatalogStudioExchanges() {}
}
