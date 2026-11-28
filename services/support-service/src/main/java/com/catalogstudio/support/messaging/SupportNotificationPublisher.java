package com.catalogstudio.support.messaging;

import com.catalogstudio.events.CatalogStudioExchanges;
import com.catalogstudio.events.support.SupportEmailNotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "catalogstudio.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class SupportNotificationPublisher {

    private final RabbitTemplate rabbitTemplate;

    public SupportNotificationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** @return true when the event was published to RabbitMQ */
    public boolean publish(SupportEmailNotificationEvent event) {
        rabbitTemplate.convertAndSend(
                CatalogStudioExchanges.DOMAIN_EVENTS,
                CatalogStudioExchanges.ROUTING_SUPPORT_EMAIL,
                event);
        return true;
    }
}
