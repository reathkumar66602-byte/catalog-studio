package com.catalogstudio.cloudcost.service;

import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse;
import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse.BudgetStatus;
import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse.DailyCost;
import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse.ExportStatus;
import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse.ProjectCost;
import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse.ServiceCost;
import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse.Summary;
import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse.TrackedResource;
import com.catalogstudio.cloudcost.service.GcpBigQueryBillingClient.CostBundle;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudCostService {

    private final CatalogStudioProperties properties;
    private final GcpBudgetClient budgetClient;
    private final GcpBigQueryBillingClient bigQueryBillingClient;

    public CloudCostDashboardResponse dashboard() {
        CatalogStudioProperties.CloudCost cfg = properties.cloudCost();
        if (cfg == null || !cfg.enabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Cloud cost dashboard is disabled");
        }

        List<CatalogStudioProperties.CloudCost.TrackedProject> tracked = trackedProjects(cfg);
        List<BudgetStatus> budgets = List.of();
        String budgetError = null;
        try {
            budgets = budgetClient.listBudgets(cfg.billingAccountId(), cfg.quotaProjectId());
        } catch (Exception ex) {
            budgetError = ex.getMessage();
            log.warn("Cloud cost budgets unavailable: {}", ex.toString());
        }

        String tableId = null;
        boolean tableReady = false;
        CostBundle costs = null;
        String costError = null;
        CatalogStudioProperties.CloudCost.BigQueryExport bq = cfg.bigQuery();
        if (bq != null && bq.enabled()) {
            try {
                Optional<String> resolved = bigQueryBillingClient.resolveTableId(bq, cfg.quotaProjectId());
                if (resolved.isPresent()) {
                    tableId = resolved.get();
                    tableReady = true;
                    costs = bigQueryBillingClient.queryCosts(bq, tableId, cfg.quotaProjectId(), tracked);
                }
            } catch (Exception ex) {
                costError = ex.getMessage();
                log.warn("Cloud cost BigQuery unavailable: {}", ex.toString());
            }
        }

        List<ProjectCost> projectCosts = costs != null ? costs.projects() : placeholderProjects(tracked);
        List<ServiceCost> services = costs != null ? costs.services() : List.of();
        List<DailyCost> daily = costs != null ? costs.daily() : List.of();

        BigDecimal mtd = projectCosts.stream()
                .map(ProjectCost::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previous = costs != null ? costs.previousMonth() : BigDecimal.ZERO;
        BigDecimal annualSpend = costs != null ? costs.annualSpend() : mtd;
        BigDecimal annualBudget = BigDecimal.valueOf(cfg.annualBudgetInr());
        BigDecimal remaining = annualBudget.subtract(annualSpend).max(BigDecimal.ZERO);
        BigDecimal percentChange = percentChange(mtd, previous);
        BigDecimal forecast = forecastMonth(mtd, daily);

        String status;
        String message;
        if (costs != null) {
            status = "OK";
            message = "Live usage from BigQuery billing export + budgets.";
        } else if (tableReady) {
            status = "PARTIAL";
            message = "Export table found but query failed: " + nullToEmpty(costError);
        } else if (bq != null && bq.enabled()) {
            status = "WAITING_FOR_EXPORT";
            message = "BigQuery dataset is ready, but the billing export table is not there yet. "
                    + "Enable Billing export (standard usage cost) to dataset gcp_billing_export in project "
                    + bq.projectId() + ". First rows usually appear within a few hours (up to 24h). "
                    + (budgetError != null ? "Budgets: " + budgetError : "Budgets loaded where possible.");
        } else {
            status = "BUDGETS_ONLY";
            message = "BigQuery export is disabled. Showing budgets and tracked resources only.";
        }
        if (budgetError != null && costs == null) {
            message = message + " Budget API: " + budgetError;
        }

        ExportStatus exportStatus = new ExportStatus(
                bq != null && bq.enabled(),
                bq == null ? "" : bq.projectId(),
                bq == null ? "" : bq.datasetId(),
                tableId == null ? "" : tableId,
                tableReady,
                "Console: Billing → Billing export → Standard usage cost → dataset gcp_billing_export"
        );

        Summary summary = new Summary(
                round(mtd),
                round(previous),
                percentChange,
                annualBudget,
                round(annualSpend),
                round(remaining),
                round(forecast)
        );

        return new CloudCostDashboardResponse(
                true,
                status,
                message,
                cfg.currencyCode(),
                cfg.billingAccountId(),
                Instant.now(),
                summary,
                projectCosts,
                services,
                daily,
                budgets,
                defaultResources(tracked),
                exportStatus
        );
    }

    private List<CatalogStudioProperties.CloudCost.TrackedProject> trackedProjects(
            CatalogStudioProperties.CloudCost cfg
    ) {
        if (cfg.projects() != null && !cfg.projects().isEmpty()) {
            return cfg.projects();
        }
        return List.of(
                new CatalogStudioProperties.CloudCost.TrackedProject(
                        "catalog-studio-prod",
                        "Catalog Studio Prod",
                        "e2-medium VM in Mumbai. Postgres runs in Docker on the VM (no Cloud SQL line)."
                ),
                new CatalogStudioProperties.CloudCost.TrackedProject(
                        "think2win-461613",
                        "Think2Win",
                        "Cloud Run + Cloud SQL (think2win-db-sg) + Redis."
                )
        );
    }

    private List<ProjectCost> placeholderProjects(List<CatalogStudioProperties.CloudCost.TrackedProject> tracked) {
        List<ProjectCost> out = new ArrayList<>();
        for (CatalogStudioProperties.CloudCost.TrackedProject p : tracked) {
            out.add(new ProjectCost(p.id(), p.name(), p.note(), BigDecimal.ZERO, BigDecimal.ZERO));
        }
        return out;
    }

    private List<TrackedResource> defaultResources(List<CatalogStudioProperties.CloudCost.TrackedProject> tracked) {
        List<TrackedResource> out = new ArrayList<>();
        for (CatalogStudioProperties.CloudCost.TrackedProject p : tracked) {
            if ("catalog-studio-prod".equals(p.id())) {
                out.add(new TrackedResource(p.id(), "Compute Engine", "catalog-studio-vm", "e2-medium · asia-south1 · 50GB disk"));
                out.add(new TrackedResource(p.id(), "Database", "Docker Postgres", "On VM — billed as Compute Engine, not Cloud SQL"));
            } else if ("think2win-461613".equals(p.id())) {
                out.add(new TrackedResource(p.id(), "Cloud SQL", "think2win-db-sg", "Postgres 16 · db-f1-micro · asia-southeast1 · 10GB SSD"));
                out.add(new TrackedResource(p.id(), "Cloud Run", "think2win-api / shritaji-web", "API + web services"));
                out.add(new TrackedResource(p.id(), "Memorystore", "Redis", "Basic M1 · Singapore"));
            }
        }
        return out;
    }

    private static BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current != null && current.compareTo(BigDecimal.ZERO) > 0
                    ? BigDecimal.valueOf(100)
                    : BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal forecastMonth(BigDecimal mtd, List<DailyCost> daily) {
        int day = LocalDate.now().getDayOfMonth();
        int daysInMonth = LocalDate.now().lengthOfMonth();
        if (day <= 0 || mtd == null) {
            return BigDecimal.ZERO;
        }
        if (daily != null && !daily.isEmpty()) {
            return mtd.multiply(BigDecimal.valueOf(daysInMonth))
                    .divide(BigDecimal.valueOf(day), 2, RoundingMode.HALF_UP);
        }
        return mtd.multiply(BigDecimal.valueOf(daysInMonth))
                .divide(BigDecimal.valueOf(day), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal round(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
