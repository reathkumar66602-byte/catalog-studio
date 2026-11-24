package com.catalogstudio.cloudcost.service;

import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse.DailyCost;
import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse.ProjectCost;
import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse.ServiceCost;
import com.catalogstudio.config.CatalogStudioProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class GcpBigQueryBillingClient {

    private final GcpAccessTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public Optional<String> resolveTableId(
            CatalogStudioProperties.CloudCost.BigQueryExport cfg,
            String quotaProjectId
    ) throws Exception {
        if (cfg.tableId() != null && !cfg.tableId().isBlank()) {
            return Optional.of(cfg.tableId().trim());
        }
        String token = tokenProvider.accessToken();
        String body = restClient.get()
                .uri("https://bigquery.googleapis.com/bigquery/v2/projects/{project}/datasets/{dataset}/tables",
                        cfg.projectId(), cfg.datasetId())
                .header("Authorization", "Bearer " + token)
                .header("x-goog-user-project", quotaProjectId)
                .retrieve()
                .body(String.class);
        JsonNode tables = objectMapper.readTree(body == null ? "{}" : body).path("tables");
        if (!tables.isArray()) {
            return Optional.empty();
        }
        String resourceTable = null;
        String standardTable = null;
        for (JsonNode table : tables) {
            String id = table.path("tableReference").path("tableId").asText("");
            if (id.startsWith("gcp_billing_export_resource_v1_")) {
                resourceTable = id;
            } else if (id.startsWith("gcp_billing_export_v1_")) {
                standardTable = id;
            }
        }
        // Prefer detailed/resource export when present; fall back to standard.
        if (resourceTable != null) {
            return Optional.of(resourceTable);
        }
        if (standardTable != null) {
            return Optional.of(standardTable);
        }
        return Optional.empty();
    }

    public CostBundle queryCosts(
            CatalogStudioProperties.CloudCost.BigQueryExport cfg,
            String tableId,
            String quotaProjectId,
            List<CatalogStudioProperties.CloudCost.TrackedProject> projects
    ) throws Exception {
        String fqTable = String.format("`%s.%s.%s`", cfg.projectId(), cfg.datasetId(), tableId);
        String projectFilter = buildProjectFilter(projects);

        // Annual window starts Sep 1 of the budget year; also covers previous calendar month.
        String sql = """
                WITH base AS (
                  SELECT
                    project.id AS project_id,
                    IFNULL(project.name, project.id) AS project_name,
                    service.description AS service,
                    DATE(usage_start_time) AS usage_date,
                    cost,
                    IFNULL((SELECT SUM(c.amount) FROM UNNEST(credits) c), 0) AS credit_amount
                  FROM %s
                  WHERE DATE(usage_start_time) >= DATE '2026-09-01'
                    %s
                )
                SELECT 'project' AS kind, project_id AS k1, project_name AS k2, '' AS k3,
                       SUM(cost) AS cost, SUM(credit_amount) AS credits
                FROM base
                WHERE usage_date >= DATE_TRUNC(CURRENT_DATE(), MONTH)
                GROUP BY project_id, project_name
                UNION ALL
                SELECT 'service', service, '', '',
                       SUM(cost), SUM(credit_amount)
                FROM base
                WHERE usage_date >= DATE_TRUNC(CURRENT_DATE(), MONTH)
                GROUP BY service
                UNION ALL
                SELECT 'daily', CAST(usage_date AS STRING), '', '',
                       SUM(cost), SUM(credit_amount)
                FROM base
                WHERE usage_date >= DATE_TRUNC(CURRENT_DATE(), MONTH)
                GROUP BY usage_date
                UNION ALL
                SELECT 'prev_month', '', '', '',
                       SUM(cost), SUM(credit_amount)
                FROM base
                WHERE usage_date >= DATE_SUB(DATE_TRUNC(CURRENT_DATE(), MONTH), INTERVAL 1 MONTH)
                  AND usage_date < DATE_TRUNC(CURRENT_DATE(), MONTH)
                UNION ALL
                SELECT 'annual', '', '', '',
                       SUM(cost), SUM(credit_amount)
                FROM base
                """.formatted(fqTable, projectFilter);

        JsonNode rows = runQuery(cfg.projectId(), quotaProjectId, sql);
        return parse(rows, projects);
    }

    private String buildProjectFilter(List<CatalogStudioProperties.CloudCost.TrackedProject> projects) {
        if (projects == null || projects.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" AND project.id IN (");
        for (int i = 0; i < projects.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('\'').append(projects.get(i).id().replace("'", "")).append('\'');
        }
        sb.append(')');
        return sb.toString();
    }

    private JsonNode runQuery(String projectId, String quotaProjectId, String sql) throws Exception {
        String token = tokenProvider.accessToken();
        String payload = objectMapper.createObjectNode()
                .put("query", sql)
                .put("useLegacySql", false)
                .put("timeoutMs", 60000)
                .toString();
        String body = restClient.post()
                .uri("https://bigquery.googleapis.com/bigquery/v2/projects/{project}/queries", projectId)
                .header("Authorization", "Bearer " + token)
                .header("x-goog-user-project", quotaProjectId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
        JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
        if (root.path("error").has("message")) {
            throw new IllegalStateException(root.path("error").path("message").asText("BigQuery query failed"));
        }
        if (!root.path("jobComplete").asBoolean(true)) {
            throw new IllegalStateException("BigQuery query did not complete in time");
        }
        return root.path("rows");
    }

    private CostBundle parse(JsonNode rows, List<CatalogStudioProperties.CloudCost.TrackedProject> projects) {
        List<ProjectCost> projectCosts = new ArrayList<>();
        List<ServiceCost> serviceCosts = new ArrayList<>();
        List<DailyCost> dailyCosts = new ArrayList<>();
        BigDecimal previousMonth = BigDecimal.ZERO;
        BigDecimal annual = BigDecimal.ZERO;

        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                JsonNode f = row.path("f");
                String kind = cell(f, 0);
                String k1 = cell(f, 1);
                String k2 = cell(f, 2);
                BigDecimal cost = decimal(cell(f, 4));
                BigDecimal credits = decimal(cell(f, 5));
                BigDecimal net = cost.add(credits);
                switch (kind) {
                    case "project" -> {
                        String note = noteFor(projects, k1);
                        projectCosts.add(new ProjectCost(k1, blankTo(k2, k1), note, cost, net));
                    }
                    case "service" -> serviceCosts.add(new ServiceCost(blankTo(k1, "Unknown"), cost, credits, net));
                    case "daily" -> dailyCosts.add(new DailyCost(k1, cost));
                    case "prev_month" -> previousMonth = cost;
                    case "annual" -> annual = cost;
                    default -> {
                    }
                }
            }
        }

        projectCosts.sort((a, b) -> b.cost().compareTo(a.cost()));
        serviceCosts.sort((a, b) -> b.cost().compareTo(a.cost()));
        dailyCosts.sort((a, b) -> a.date().compareTo(b.date()));
        return new CostBundle(projectCosts, serviceCosts, dailyCosts, previousMonth, annual);
    }

    private static String noteFor(List<CatalogStudioProperties.CloudCost.TrackedProject> projects, String id) {
        if (projects == null) {
            return "";
        }
        return projects.stream()
                .filter(p -> id.equals(p.id()))
                .map(CatalogStudioProperties.CloudCost.TrackedProject::note)
                .findFirst()
                .orElse("");
    }

    private static String cell(JsonNode f, int index) {
        if (f == null || !f.isArray() || f.size() <= index) {
            return "";
        }
        return f.get(index).path("v").asText("");
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record CostBundle(
            List<ProjectCost> projects,
            List<ServiceCost> services,
            List<DailyCost> daily,
            BigDecimal previousMonth,
            BigDecimal annualSpend
    ) {}
}
