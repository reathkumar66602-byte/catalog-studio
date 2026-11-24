package com.catalogstudio.supplier.messaging;

import com.catalogstudio.events.CatalogStudioExchanges;
import com.catalogstudio.events.supplier.SupplierStoreCreatedEvent;
import com.catalogstudio.events.supplier.WholesaleOrderLifecycleEvent;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DomainEventPublisher {

    private final Optional<RabbitTemplate> rabbitTemplate;

    @Autowired
    public DomainEventPublisher(@Autowired(required = false) RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = Optional.ofNullable(rabbitTemplate);
    }

    public void storeCreated(SupplierStoreCreatedEvent event) {
        publish(CatalogStudioExchanges.ROUTING_SUPPLIER_STORE_CREATED, event);
    }

    public void orderLifecycle(WholesaleOrderLifecycleEvent event) {
        String routing = switch (event.type()) {
            case WholesaleOrderLifecycleEvent.ACCEPTED, WholesaleOrderLifecycleEvent.BILL_READY
                    -> CatalogStudioExchanges.ROUTING_WHOLESALE_ORDER_ACCEPTED;
            case WholesaleOrderLifecycleEvent.REJECTED -> CatalogStudioExchanges.ROUTING_WHOLESALE_ORDER_REJECTED;
            case WholesaleOrderLifecycleEvent.FULFILLED -> CatalogStudioExchanges.ROUTING_WHOLESALE_ORDER_FULFILLED;
            default -> CatalogStudioExchanges.ROUTING_WHOLESALE_ORDER_PLACED;
        };
        publish(routing, event);
    }

    private void publish(String routingKey, Object event) {
        if (rabbitTemplate.isEmpty()) {
            log.debug("Messaging disabled; skipped event {}", routingKey);
            return;
        }
        try {
            rabbitTemplate.get().convertAndSend(CatalogStudioExchanges.DOMAIN_EVENTS, routingKey, event);
        } catch (Exception ex) {
            log.warn("Failed to publish domain event {}: {}", routingKey, ex.toString());
        }
    }
}
