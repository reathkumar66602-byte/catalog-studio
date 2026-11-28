package com.catalogstudio.events.config;

import com.catalogstudio.events.CatalogStudioExchanges;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(TopicExchange.class)
@ConditionalOnProperty(name = "catalogstudio.messaging.enabled", havingValue = "true", matchIfMissing = false)
public class DomainEventsRabbitConfig {

    @Bean
    public TopicExchange catalogStudioDomainExchange() {
        return new TopicExchange(CatalogStudioExchanges.DOMAIN_EVENTS, true, false);
    }

    @Bean
    public Queue platformSupplierNotificationsQueue() {
        return new Queue(CatalogStudioExchanges.QUEUE_PLATFORM_SUPPLIER_NOTIFICATIONS, true);
    }

    @Bean
    public Binding supplierStoreCreatedBinding(Queue platformSupplierNotificationsQueue, TopicExchange catalogStudioDomainExchange) {
        return BindingBuilder.bind(platformSupplierNotificationsQueue)
                .to(catalogStudioDomainExchange)
                .with("supplier.#");
    }

    @Bean
    public Binding wholesaleOrderBinding(Queue platformSupplierNotificationsQueue, TopicExchange catalogStudioDomainExchange) {
        return BindingBuilder.bind(platformSupplierNotificationsQueue)
                .to(catalogStudioDomainExchange)
                .with("wholesale.#");
    }

    @Bean
    public Queue platformSupportNotificationsQueue() {
        return new Queue(CatalogStudioExchanges.QUEUE_PLATFORM_SUPPORT_NOTIFICATIONS, true);
    }

    @Bean
    public Binding supportEmailBinding(Queue platformSupportNotificationsQueue, TopicExchange catalogStudioDomainExchange) {
        return BindingBuilder.bind(platformSupportNotificationsQueue)
                .to(catalogStudioDomainExchange)
                .with("support.#");
    }

    @Bean
    public MessageConverter domainEventsMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
