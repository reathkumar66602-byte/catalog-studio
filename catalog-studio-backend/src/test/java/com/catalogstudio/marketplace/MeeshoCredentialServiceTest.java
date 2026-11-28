package com.catalogstudio.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.marketplace.crypto.CredentialCrypto;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.entity.MarketplaceConnection.ConnectionStatus;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient;
import com.catalogstudio.marketplace.repository.MarketplaceConnectionRepository;
import com.catalogstudio.marketplace.service.MeeshoCredentialService;
import com.catalogstudio.marketplace.service.MeeshoPanelConnectionService;
import com.catalogstudio.user.entity.User;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeeshoCredentialServiceTest {

    @Mock MarketplaceConnectionRepository connectionRepository;
    @Mock MeeshoPanelClient meeshoPanelClient;

    CredentialCrypto crypto;
    MeeshoCredentialService service;

    @BeforeEach
    void setUp() {
        CatalogStudioProperties properties = new CatalogStudioProperties(
                new CatalogStudioProperties.Jwt("secret-secret-secret-secret-secret-12", 15, 7),
                new CatalogStudioProperties.Cors(java.util.List.of("http://localhost:5173")),
                new CatalogStudioProperties.Storage("local", "./uploads", "http://localhost"),
                new CatalogStudioProperties.Ai("mock", "", "mock", 30, "classpath:x", "gpt-image-1", "high", "high"),
                new CatalogStudioProperties.Upload(5, 1, java.util.List.of("image/jpeg")),
                new CatalogStudioProperties.Security(60, 10),
                new CatalogStudioProperties.Seed("a", "b", "c", "d"),
                new CatalogStudioProperties.Mail(false, "zoho", "h", 587, "", "", "", "CS", true, "", "", ""),
                new CatalogStudioProperties.Otp(false, 6, 10, 5, 60, false),
                new CatalogStudioProperties.Google(""),
                new CatalogStudioProperties.Billing("91"),
                new CatalogStudioProperties.Tingily("https://x", "", "", "", 40),
                new CatalogStudioProperties.Whatsapp(false, "", "", "", "", "", "", "body"),
                new CatalogStudioProperties.MeeshoPanel("https://supplier.meesho.com", false),
                new CatalogStudioProperties.Oms(
                        new CatalogStudioProperties.Oms.Flipkart(false, "", "", ""),
                        new CatalogStudioProperties.Oms.Amazon(false, "", "", "", "A21TJRUUN4KGV", "", true),
                        new CatalogStudioProperties.Oms.Meesho(
                                true,
                                "shared@shop.com",
                                "shared-pass",
                                "https://supplier.meesho.com",
                                false,
                                false,
                                true,
                                300000L,
                                true,
                                true)),
                new CatalogStudioProperties.Wholesale(new CatalogStudioProperties.Wholesale.Gst(
                        "mock", "", "", 0.72, "https://www.gstinapi.in", "")),
                new CatalogStudioProperties.CloudCost(
                        true,
                        "012AA3-8490F8-BDB5E6",
                        "INR",
                        30000,
                        "catalog-studio-prod",
                        new CatalogStudioProperties.CloudCost.BigQueryExport(
                                true, "catalog-studio-prod", "gcp_billing_export", ""),
                        java.util.List.of()),
                new CatalogStudioProperties.Modules(true, "catalog-studio-internal"));
        crypto = new CredentialCrypto(properties);
        service = new MeeshoCredentialService(properties, connectionRepository, meeshoPanelClient, crypto);
    }

    @Test
    void multiUserUsesSellerOwnMeeshoNotPropertyFile() {
        User seller = User.builder().id(7L).email("seller@a.com").build();
        MarketplaceConnection connection = MarketplaceConnection.builder()
                .user(seller)
                .marketplace(MeeshoPanelConnectionService.MARKETPLACE)
                .connectionType(MeeshoPanelConnectionService.TYPE_PANEL_LOGIN)
                .status(ConnectionStatus.CONNECTED)
                .configurationJson(Map.of(
                        "email", "sellerA@meesho.com",
                        "passwordEnc", crypto.encrypt("seller-a-pass")))
                .build();
        when(connectionRepository.findByUserIdAndMarketplace(7L, "MEESHO")).thenReturn(Optional.of(connection));

        var resolved = service.resolve(7L);

        assertThat(resolved.email()).isEqualTo("sellerA@meesho.com");
        assertThat(resolved.password()).isEqualTo("seller-a-pass");
        assertThat(resolved.source()).isEqualTo("CONNECTION_DB");
        assertThat(resolved.email()).isNotEqualTo("shared@shop.com");
    }

    @Test
    void multiUserWithoutConnectionDoesNotFallBackToHardcodedMeesho() {
        when(connectionRepository.findByUserIdAndMarketplace(8L, "MEESHO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(8L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Connect your own Meesho");
        assertThat(service.hasUserConnection(8L)).isFalse();
    }
}
