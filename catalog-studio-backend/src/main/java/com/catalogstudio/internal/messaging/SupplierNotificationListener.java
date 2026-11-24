package com.catalogstudio.internal.messaging;

import com.catalogstudio.email.service.MailDispatchService;
import com.catalogstudio.events.CatalogStudioExchanges;
import com.catalogstudio.events.supplier.WholesaleOrderLifecycleEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "catalogstudio.messaging.enabled", havingValue = "true")
public class SupplierNotificationListener {

    private final MailDispatchService mailDispatchService;

    @RabbitListener(queues = CatalogStudioExchanges.QUEUE_PLATFORM_SUPPLIER_NOTIFICATIONS)
    public void onSupplierEvent(WholesaleOrderLifecycleEvent event) {
        if (event == null) {
            return;
        }
        if (!WholesaleOrderLifecycleEvent.BILL_READY.equals(event.type())
                && !WholesaleOrderLifecycleEvent.ACCEPTED.equals(event.type())) {
            log.debug("Ignoring supplier event type={}", event.type());
            return;
        }
        if (!StringUtils.hasText(event.buyerEmail()) || !StringUtils.hasText(event.billPdfUrl())) {
            return;
        }
        String name = StringUtils.hasText(event.buyerName()) ? event.buyerName() : "there";
        String store = StringUtils.hasText(event.storeName()) ? event.storeName() : "your supplier";
        String subject = "Invoice " + event.orderNumber() + " from " + store;
        String html = """
                <p>Hello %s,</p>
                <p>Your GST invoice <strong>%s</strong> from <strong>%s</strong> is ready.</p>
                <p>Grand total: <strong>INR %s</strong></p>
                <p><a href="%s">Download bill PDF</a></p>
                """.formatted(
                escape(name),
                escape(event.orderNumber()),
                escape(store),
                event.grandTotal() == null ? "0" : event.grandTotal().toPlainString(),
                escape(event.billPdfUrl()));
        String text = "Invoice " + event.orderNumber() + " from " + store + ". Download: " + event.billPdfUrl();
        try {
            mailDispatchService.sendHtml(event.buyerEmail().trim(), subject, html, text);
        } catch (Exception ex) {
            log.warn("Failed to email wholesale bill {}: {}", event.orderNumber(), ex.toString());
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
