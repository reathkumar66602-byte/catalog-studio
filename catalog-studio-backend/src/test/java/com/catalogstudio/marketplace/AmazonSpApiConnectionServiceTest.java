package com.catalogstudio.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.marketplace.crypto.CredentialCrypto;
import com.catalogstudio.marketplace.dto.AmazonSpApiConnectRequest;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.repository.MarketplaceConnectionRepository;
import com.catalogstudio.marketplace.service.AmazonSpApiConnectionService;
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
class AmazonSpApiConnectionServiceTest {

    @Mock MarketplaceConnectionRepository connectionRepository;
    @Mock UserRepository userRepository;

    CredentialCrypto crypto;
    AmazonSpApiConnectionService service;

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
        service = new AmazonSpApiConnectionService(connectionRepository, userRepository, crypto);
    }

    @Test
    void connectEncryptsSecretAndRefreshToken() {
        when(connectionRepository.findByUserIdAndMarketplace(1L, "AMAZON")).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().id(1L).build());
        when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.connect(1L, new AmazonSpApiConnectRequest(
                "amzn1.application-oa2-client.abc12345",
                "amzn1.oa2-cs.v1.supersecretvalue",
                "Atzr|IwEBIExampleRefreshTokenValue1234567890",
                null,
                "Amazon sandbox",
                true));

        assertThat(response.status()).isEqualTo("CONNECTED");
        assertThat(response.connectionType()).isEqualTo("SP_API");
        assertThat(response.sandbox()).isTrue();
        assertThat(response.marketplaceId()).isEqualTo("A21TJRUUN4KGV");
        assertThat(response.refreshTokenConfigured()).isTrue();

        ArgumentCaptor<MarketplaceConnection> captor = ArgumentCaptor.forClass(MarketplaceConnection.class);
        verify(connectionRepository).save(captor.capture());
        assertThat(captor.getValue().getConfigurationJson().get("clientSecretEnc").toString()).startsWith("enc:v1:");
        assertThat(captor.getValue().getConfigurationJson().get("refreshTokenEnc").toString()).startsWith("enc:v1:");
    }
}
