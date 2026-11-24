package com.catalogstudio.subscription.repository;

import com.catalogstudio.subscription.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    boolean existsByProviderAndEventKey(String provider, String eventKey);
}
