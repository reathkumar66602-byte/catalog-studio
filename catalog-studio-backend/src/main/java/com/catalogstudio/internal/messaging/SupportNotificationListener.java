package com.catalogstudio.internal.messaging;

import com.catalogstudio.events.CatalogStudioExchanges;
import com.catalogstudio.events.support.SupportEmailNotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalogstudio.messaging.enabled", havingValue = "true")
public class SupportNotificationListener {

    private final SupportEmailDispatch supportEmailDispatch;

    @RabbitListener(queues = CatalogStudioExchanges.QUEUE_PLATFORM_SUPPORT_NOTIFICATIONS)
    public void onSupportEmail(SupportEmailNotificationEvent event) {
        supportEmailDispatch.dispatch(event);
    }
}
