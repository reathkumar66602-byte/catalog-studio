package com.catalogstudio.cloudcost.service;

import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse.BudgetStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class GcpBudgetClient {

    private final GcpAccessTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public List<BudgetStatus> listBudgets(String billingAccountId, String quotaProjectId) throws Exception {
        String token = tokenProvider.accessToken();
        String body = restClient.get()
                .uri("https://billingbudgets.googleapis.com/v1/billingAccounts/{id}/budgets", billingAccountId)
                .header("Authorization", "Bearer " + token)
                .header("x-goog-user-project", quotaProjectId)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        JsonNode budgets = root.path("budgets");
        List<BudgetStatus> out = new ArrayList<>();
        if (!budgets.isArray()) {
            return out;
        }
        for (JsonNode budget : budgets) {
            out.add(mapBudget(budget));
        }
        return out;
    }

    private BudgetStatus mapBudget(JsonNode budget) {
        String name = budget.path("name").asText("");
        String id = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
        String displayName = budget.path("displayName").asText(id);

        BigDecimal amount = BigDecimal.ZERO;
        JsonNode specified = budget.path("amount").path("specifiedAmount");
        if (!specified.isMissingNode()) {
            long units = specified.path("units").asLong(0);
            int nanos = specified.path("nanos").asInt(0);
            amount = BigDecimal.valueOf(units).add(BigDecimal.valueOf(nanos, 9));
        }

        List<Double> thresholds = new ArrayList<>();
        JsonNode rules = budget.path("thresholdRules");
        if (rules.isArray()) {
            for (JsonNode rule : rules) {
                thresholds.add(rule.path("thresholdPercent").asDouble());
            }
        }

        JsonNode filter = budget.path("budgetFilter");
        List<String> projectIds = new ArrayList<>();
        JsonNode projects = filter.path("projects");
        if (projects.isArray()) {
            for (JsonNode p : projects) {
                String raw = p.asText("");
                projectIds.add(raw.replace("projects/", ""));
            }
        }

        String periodLabel = "Custom";
        if (filter.hasNonNull("calendarPeriod")) {
            periodLabel = filter.path("calendarPeriod").asText("MONTH");
        } else if (filter.path("customPeriod").has("startDate")) {
            JsonNode start = filter.path("customPeriod").path("startDate");
            JsonNode end = filter.path("customPeriod").path("endDate");
            periodLabel = formatDate(start) + " – " + formatDate(end);
        }

        JsonNode notifications = budget.path("notificationsRule");
        boolean pubsub = notifications.hasNonNull("pubsubTopic")
                && !notifications.path("pubsubTopic").asText("").isBlank();
        String topic = notifications.path("pubsubTopic").asText("");
        boolean emailConfigured = !notifications.path("disableDefaultIamRecipients").asBoolean(false)
                || (notifications.path("monitoringNotificationChannels").isArray()
                && !notifications.path("monitoringNotificationChannels").isEmpty());
        // Empty notificationsRule still emails Billing Account Admins by default.
        if (notifications.isMissingNode() || notifications.isEmpty()
                || (notifications.isObject() && notifications.size() == 0)) {
            emailConfigured = true;
        }
        // Pub/Sub-only configs still keep default IAM email unless disabled.
        if (pubsub && !notifications.path("disableDefaultIamRecipients").asBoolean(false)) {
            emailConfigured = true;
        }

        String creditTreatment = filter.path("creditTypesTreatment").asText("INCLUDE_ALL_CREDITS");

        return new BudgetStatus(
                id,
                displayName,
                periodLabel,
                amount,
                thresholds,
                emailConfigured,
                pubsub,
                topic,
                projectIds,
                creditTreatment
        );
    }

    private static String formatDate(JsonNode d) {
        if (d == null || d.isMissingNode()) {
            return "?";
        }
        return d.path("year").asInt() + "-"
                + String.format("%02d", d.path("month").asInt()) + "-"
                + String.format("%02d", d.path("day").asInt());
    }
}
