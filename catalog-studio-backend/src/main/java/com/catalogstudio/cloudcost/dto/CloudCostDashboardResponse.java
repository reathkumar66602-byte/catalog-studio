package com.catalogstudio.cloudcost.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CloudCostDashboardResponse(
        boolean enabled,
        String status,
        String message,
        String currencyCode,
        String billingAccountId,
        Instant generatedAt,
        Summary summary,
        List<ProjectCost> projects,
        List<ServiceCost> services,
        List<DailyCost> daily,
        List<BudgetStatus> budgets,
        List<TrackedResource> resources,
        ExportStatus exportStatus
) {
    public record Summary(
            BigDecimal monthToDate,
            BigDecimal previousMonth,
            BigDecimal percentChange,
            BigDecimal annualBudget,
            BigDecimal periodSpendAgainstAnnual,
            BigDecimal remainingAgainstAnnual,
            BigDecimal forecastMonth
    ) {}

    public record ProjectCost(
            String projectId,
            String name,
            String note,
            BigDecimal cost,
            BigDecimal netCost
    ) {}

    public record ServiceCost(
            String service,
            BigDecimal cost,
            BigDecimal credits,
            BigDecimal netCost
    ) {}

    public record DailyCost(
            String date,
            BigDecimal cost
    ) {}

    public record BudgetStatus(
            String id,
            String displayName,
            String periodLabel,
            BigDecimal amount,
            List<Double> thresholds,
            boolean emailAlertsConfigured,
            boolean pubsubConfigured,
            String pubsubTopic,
            List<String> projectIds,
            String creditTreatment
    ) {}

    public record TrackedResource(
            String projectId,
            String kind,
            String name,
            String detail
    ) {}

    public record ExportStatus(
            boolean bigQueryConfigured,
            String projectId,
            String datasetId,
            String tableId,
            boolean tableReady,
            String hint
    ) {}
}
