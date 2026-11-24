package com.catalogstudio.wholesale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.supplier.config.SupplierProperties;
import com.catalogstudio.wholesale.service.GstVerificationService;
import com.catalogstudio.wholesale.service.GstVerificationService.GstVerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class GstVerificationServiceTest {

    private static final String VALID_GSTIN = "27AAPFU0939F1ZV";

    private final GstVerificationService mock = service("mock");

    private static GstVerificationService service(String provider) {
        SupplierProperties properties = new SupplierProperties(
                new SupplierProperties.Jwt("secret-secret-secret-secret-secret-12", 15, 7),
                new SupplierProperties.Cors(List.of("http://localhost:5173")),
                new SupplierProperties.Storage("local", "./target/test-uploads", "http://localhost"),
                new SupplierProperties.Upload(10485760, 5, List.of("image/jpeg")),
                new SupplierProperties.Platform("http://localhost:8082", "test-key"),
                new SupplierProperties.Wholesale(
                        new SupplierProperties.Wholesale.Gst(provider, "", "", 0.72, "https://www.gstinapi.in", "")));
        return new GstVerificationService(properties, new ObjectMapper());
    }

    @Test
    void mockProviderAcceptsValidGstinAndKeepsMatchedStoreName() {
        GstVerificationResult result = mock.verify(" " + VALID_GSTIN.toLowerCase() + " ", "Star Fashions");
        assertThat(result.gstin()).isEqualTo(VALID_GSTIN);
        assertThat(result.matchedName()).isEqualTo("Star Fashions");
        assertThat(result.tradeName()).isNotBlank();
    }

    @Test
    void rejectsMalformedGstin() {
        assertThatThrownBy(() -> mock.verify("27AAPFU0939F1Z", "Star"))
                .isInstanceOf(ApiException.class);
        assertThat(GstVerificationService.isValidFormat("00AAPFU0939F1ZV")).isFalse();
    }

    @Test
    void checksumAcceptsKnownGstinAndRejectsTypos() {
        assertThat(GstVerificationService.hasValidChecksum(VALID_GSTIN)).isTrue();
        assertThat(GstVerificationService.hasValidChecksum("27AAPFU0939F1ZA")).isFalse();
    }

    @Test
    void normalizationStripsCompanyFormsAndPunctuation() {
        assertThat(GstVerificationService.normalizeName("M/s. Star Fashions Pvt. Ltd."))
                .isEqualTo("STAR FASHIONS");
        assertThat(GstVerificationService.similarity("Star Fashions Private Limited", "STAR  FASHIONS"))
                .isEqualTo(1.0);
    }

    @Test
    void mismatchedLegalNameFailsAndTradeNameCanMatch() {
        GstVerificationService service = service("http");
        assertThatThrownBy(() -> service.matchName(VALID_GSTIN, "Royal Electronics", "STAR FASHIONS PVT LTD", null, 0.72))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not match");
        GstVerificationResult byTrade = service.matchName(
                VALID_GSTIN, "Star Fashions", "ABC TRADERS PRIVATE LIMITED", "STAR FASHIONS", 0.72);
        assertThat(byTrade.matchedName()).isEqualTo("STAR FASHIONS");
    }

    @Test
    void gstinapiResponseParsesLegalAndTradeName() throws Exception {
        var mapper = new ObjectMapper();
        var root = mapper.readTree("""
                {
                  "success": true,
                  "gstin": "00AAAAA0000A1ZT",
                  "legal_name": "GSTINAPI TEST PRIVATE LIMITED",
                  "trade_name": "GSTINAPI SANDBOX",
                  "status": "Active"
                }
                """);
        var registered = GstVerificationService.parseGstinApiResponse(root);
        assertThat(registered.legalName()).isEqualTo("GSTINAPI TEST PRIVATE LIMITED");
        assertThat(registered.tradeName()).isEqualTo("GSTINAPI SANDBOX");
    }
}
