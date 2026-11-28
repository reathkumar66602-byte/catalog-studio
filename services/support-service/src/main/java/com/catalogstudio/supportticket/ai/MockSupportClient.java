package com.catalogstudio.supportticket.ai;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MockSupportClient implements SupportAiClient {

    @Override
    public AiReply reply(String locale, List<ChatTurn> history, String userMessage, String kbContext) {
        String msg = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        if (containsAny(msg, "password", "otp", "api key", "secret", "refund", "cancel plan")) {
            return AiReply.escalate(SupportAiPrompts.escalateMessage(locale));
        }
        if (containsAny(msg, "meesho")) {
            return new AiReply(
                    "CatalogStudio can sync Meesho via the supplier-panel OMS path when enabled. "
                            + "Use Marketplaces / OMS in the dashboard for connection and order import. "
                            + "Do not share Meesho passwords here. If sync fails, create a Marketplace ticket.",
                    true,
                    false);
        }
        if (containsAny(msg, "amazon", "flipkart")) {
            return new AiReply(
                    "Amazon (SP-API) and Flipkart (API Partner) connections are managed as OMS integrations. "
                            + "OAuth/partner credentials are configured by the platform — never paste secrets in chat. "
                            + "Open Marketplaces settings or create a ticket if a connection error persists.",
                    true,
                    false);
        }
        if (containsAny(msg, "wholesale", "supplier", "gstin", "gst")) {
            return new AiReply(
                    "The wholesale Live Stock module lets suppliers verify GSTIN, publish wholesale products, "
                            + "and fulfil retailer orders with GST bills. Retailers browse and place orders from the wholesale catalog.",
                    true,
                    false);
        }
        if (containsAny(msg, "billing", "subscription", "plan", "quota", "payment", "cashfree")) {
            return new AiReply(
                    "Plans control CatalogStudio feature access and quotas (products, photos, shoot). "
                            + "Payments go through the configured billing/payment flow. "
                            + "I cannot change plans or issue refunds — create a Billing ticket with your payment reference.",
                    true,
                    false);
        }
        if (containsAny(msg, "shoot", "photo", "image", "ai")) {
            return new AiReply(
                    "Product shoot / AI image tools prepare listing photos and consume plan photo quotas. "
                            + "Use Products / Shoot in the dashboard. If a generation fails, create a Technical ticket with the error time.",
                    true,
                    false);
        }
        if (containsAny(msg, "order", "oms", "label", "courier", "shiprocket", "delhivery")) {
            return new AiReply(
                    "The OMS board collects marketplace orders for fulfilment and can generate courier labels when integrations are configured. "
                            + "Include the order number in a ticket if a label or sync fails.",
                    true,
                    false);
        }
        if (kbContext != null && !kbContext.isBlank() && kbContext.length() > 40) {
            String snippet = kbContext.length() > 600 ? kbContext.substring(0, 600) + "…" : kbContext;
            return new AiReply(
                    "Here is what CatalogStudio docs say that may help:\\n\\n" + snippet
                            + "\\n\\nIf this does not answer your question, create a support ticket.",
                    true,
                    false);
        }
        return AiReply.escalate(SupportAiPrompts.escalateMessage(locale));
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
