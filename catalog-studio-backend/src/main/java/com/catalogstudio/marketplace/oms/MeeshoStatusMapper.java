package com.catalogstudio.marketplace.oms;

import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Maps Catalog Studio OMS statuses ↔ Meesho panel statuses (Base.com style).
 */
@Component
public class MeeshoStatusMapper {

    public String toInternal(String channelStatus) {
        if (!StringUtils.hasText(channelStatus)) {
            return "NEW";
        }
        String s = channelStatus.trim().toLowerCase(Locale.ROOT);
        if (s.contains("cancel")) {
            return "CANCELLED";
        }
        if (s.contains("return") || s.contains("rto") || s.contains("in_transit_return")) {
            return "RETURN";
        }
        if (s.contains("deliver") || s.contains("complete")) {
            return "DELIVERED";
        }
        // "Ready to ship" must win over the substring "ship"
        if (s.contains("ready") || s.contains("pack")) {
            return "PACKED";
        }
        if (s.contains("dispatch") || s.contains("shipped") || s.equals("ship") || s.contains("ship ")) {
            return "SHIPPED";
        }
        if (s.contains("ship")) {
            return "SHIPPED";
        }
        if (s.contains("accept") || s.contains("process") || s.contains("confirm")) {
            return "PROCESSING";
        }
        if (s.contains("hold") || s.contains("pending") || s.contains("new")) {
            return "NEW";
        }
        return channelStatus.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    public String toMeesho(String internalStatus) {
        if (!StringUtils.hasText(internalStatus)) {
            return "Pending";
        }
        return switch (internalStatus.trim().toUpperCase(Locale.ROOT)) {
            case "NEW", "PENDING", "HOLD" -> "Pending";
            case "PROCESSING", "ACCEPTED" -> "Accepted";
            case "PACKED", "READY_TO_SHIP" -> "Ready to ship";
            case "SHIPPED", "DISPATCHED" -> "Dispatched";
            case "DELIVERED", "COMPLETED" -> "Delivered";
            case "CANCELLED", "CANCELED" -> "Cancelled";
            case "RETURN", "RETURNING" -> "Return";
            default -> internalStatus;
        };
    }

    public boolean isTerminalCancel(String internalStatus) {
        return "CANCELLED".equalsIgnoreCase(internalStatus) || "CANCELED".equalsIgnoreCase(internalStatus);
    }

    public boolean isShipped(String internalStatus) {
        return "SHIPPED".equalsIgnoreCase(internalStatus) || "DISPATCHED".equalsIgnoreCase(internalStatus)
                || "DELIVERED".equalsIgnoreCase(internalStatus);
    }

    public boolean isReturn(String internalStatus) {
        return "RETURN".equalsIgnoreCase(internalStatus) || "RETURNING".equalsIgnoreCase(internalStatus);
    }
}
