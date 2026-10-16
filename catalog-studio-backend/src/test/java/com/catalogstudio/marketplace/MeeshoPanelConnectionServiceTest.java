package com.catalogstudio.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.marketplace.crypto.CredentialCrypto;
import com.catalogstudio.marketplace.dto.MeeshoPanelConnectRequest;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.entity.MarketplaceConnection.ConnectionStatus;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient;
import com.catalogstudio.marketplace.meesho.MeeshoPanelClient.AuthResult;
import com.catalogstudio.marketplace.meesho.MeeshoPanelSession;
import com.catalogstudio.marketplace.repository.MarketplaceChannelOrderRepository;
import com.catalogstudio.marketplace.repository.MarketplaceConnectionRepository;
import com.catalogstudio.marketplace.service.MeeshoPanelConnectionService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeeshoPanelConnectionServiceTest {

    @Mock MarketplaceConnectionRepository connectionRepository;
    @Mock MarketplaceChannelOrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock MeeshoPanelClient meeshoPanelClient;

    CredentialCrypto crypto;
    MeeshoPanelConnectionService service;

    @BeforeEach
    void setUp() {
        crypto = new CredentialCrypto(new CatalogStudioProperties(
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
                new CatalogStudioProperties.Whatsapp(false, "", ""),
                new CatalogStudioProperties.MeeshoPanel("https://supplier.meesho.com", false),
                new CatalogStudioProperties.Oms(
                        new CatalogStudioProperties.Oms.Flipkart(false, "", "", ""),
                        new CatalogStudioProperties.Oms.Amazon(false, "", "", "", "A21TJRUUN4KGV", true),
                        new CatalogStudioProperties.Oms.Meesho(false, "", "", "https://supplier.meesho.com", false)),
                new CatalogStudioProperties.Wholesale(new CatalogStudioProperties.Wholesale.Gst(
                        "mock", "", "", 0.72, "https://www.gstinapi.in", ""))));
        service = new MeeshoPanelConnectionService(
                connectionRepository, orderRepository, userRepository, meeshoPanelClient, crypto);
    }

    @Test
    void connectStoresEncryptedPasswordAndPanelType() {
        when(meeshoPanelClient.authenticate(anyString(), anyString())).thenReturn(
                AuthResult.ok(MeeshoPanelSession.unverified("seller@shop.in"), "saved"));
        when(connectionRepository.findByUserIdAndMarketplace(1L, "MEESHO")).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().id(1L).build());
        when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.countByUserIdAndMarketplaceAndStatusIgnoreCase(anyLong(), anyString(), anyString()))
                .thenReturn(0L);

        var response = service.connect(1L, new MeeshoPanelConnectRequest(
                "seller@shop.in", "SecretPass1", "Meesho main"));

        assertThat(response.status()).isEqualTo("CONNECTED");
        assertThat(response.connectionType()).isEqualTo("PANEL_LOGIN");
        assertThat(response.emailMasked()).contains("@shop.in");
        assertThat(response.passwordConfigured()).isTrue();

        ArgumentCaptor<MarketplaceConnection> captor = ArgumentCaptor.forClass(MarketplaceConnection.class);
        verify(connectionRepository).save(captor.capture());
        MarketplaceConnection saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(saved.getConfigurationJson().get("passwordEnc").toString()).startsWith("enc:v1:");
        assertThat(crypto.decrypt(saved.getConfigurationJson().get("passwordEnc").toString()))
                .isEqualTo("SecretPass1");
    }

    @Test
    void connectRejectsFailedAuth() {
        when(meeshoPanelClient.authenticate(eq("bad@shop.in"), anyString()))
                .thenReturn(AuthResult.failed("Meesho rejected email/password"));

        assertThatThrownBy(() -> service.connect(1L, new MeeshoPanelConnectRequest(
                        "bad@shop.in", "wrong-password", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("rejected");
    }
}
