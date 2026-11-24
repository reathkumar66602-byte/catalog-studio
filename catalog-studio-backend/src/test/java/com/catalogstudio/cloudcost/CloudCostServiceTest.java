package com.catalogstudio.cloudcost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.catalogstudio.cloudcost.dto.CloudCostDashboardResponse;
import com.catalogstudio.cloudcost.service.CloudCostService;
import com.catalogstudio.cloudcost.service.GcpBigQueryBillingClient;
import com.catalogstudio.cloudcost.service.GcpBudgetClient;
import com.catalogstudio.config.CatalogStudioProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CloudCostServiceTest {

    @Mock
    private GcpBudgetClient budgetClient;
    @Mock
    private GcpBigQueryBillingClient bigQueryBillingClient;

    private CloudCostService service;

    @BeforeEach
    void setUp() {
        CatalogStudioProperties.CloudCost cloudCost = new CatalogStudioProperties.CloudCost(
                true,
                "012AA3-8490F8-BDB5E6",
                "INR",
                30000,
                "catalog-studio-prod",
                new CatalogStudioProperties.CloudCost.BigQueryExport(
                        true, "catalog-studio-prod", "gcp_billing_export", ""),
                List.of(
                        new CatalogStudioProperties.CloudCost.TrackedProject(
                                "catalog-studio-prod", "Catalog Studio Prod", "VM note"),
                        new CatalogStudioProperties.CloudCost.TrackedProject(
                                "think2win-461613", "Think2Win", "SQL note")
                )
        );
        CatalogStudioProperties properties = org.mockito.Mockito.mock(CatalogStudioProperties.class);
        when(properties.cloudCost()).thenReturn(cloudCost);
        service = new CloudCostService(properties, budgetClient, bigQueryBillingClient);
    }

    @Test
    void waitingForExportWhenTableMissing() throws Exception {
        when(budgetClient.listBudgets(anyString(), anyString())).thenReturn(List.of());
        when(bigQueryBillingClient.resolveTableId(any(), anyString())).thenReturn(Optional.empty());

        CloudCostDashboardResponse response = service.dashboard();

        assertThat(response.status()).isEqualTo("WAITING_FOR_EXPORT");
        assertThat(response.summary().annualBudget()).isEqualByComparingTo("30000");
        assertThat(response.projects()).hasSize(2);
        assertThat(response.exportStatus().datasetId()).isEqualTo("gcp_billing_export");
        assertThat(response.resources()).isNotEmpty();
    }

    @Test
    void okWhenBigQueryReturnsCosts() throws Exception {
        when(budgetClient.listBudgets(anyString(), anyString())).thenReturn(List.of());
        when(bigQueryBillingClient.resolveTableId(any(), anyString()))
                .thenReturn(Optional.of("gcp_billing_export_v1_012AA3_8490F8_BDB5E6"));
        when(bigQueryBillingClient.queryCosts(any(), anyString(), anyString(), any()))
                .thenReturn(new GcpBigQueryBillingClient.CostBundle(
                        List.of(new CloudCostDashboardResponse.ProjectCost(
                                "catalog-studio-prod",
                                "Catalog Studio Prod",
                                "VM note",
                                new BigDecimal("100.00"),
                                new BigDecimal("90.00"))),
                        List.of(new CloudCostDashboardResponse.ServiceCost(
                                "Compute Engine",
                                new BigDecimal("100.00"),
                                new BigDecimal("-10.00"),
                                new BigDecimal("90.00"))),
                        List.of(new CloudCostDashboardResponse.DailyCost("2026-11-01", new BigDecimal("100.00"))),
                        new BigDecimal("50.00"),
                        new BigDecimal("10443.00")
                ));

        CloudCostDashboardResponse response = service.dashboard();

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.summary().monthToDate()).isEqualByComparingTo("100.00");
        assertThat(response.summary().remainingAgainstAnnual()).isEqualByComparingTo("19557.00");
        assertThat(response.services()).hasSize(1);
    }
}
