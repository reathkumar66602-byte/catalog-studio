package com.catalogstudio.wholesale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.wholesale.service.GstVerificationService;
import com.catalogstudio.wholesale.service.GstVerificationService.GstVerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class GstVerificationServiceTest {

    private static final String VALID_GSTIN = "27AAPFU0939F1ZV";

    private final GstVerificationService mock = service("mock");

    private static GstVerificationService service(String provider) {
        CatalogStudioProperties properties = new CatalogStudioProperties(
                new CatalogStudioProperties.Jwt("secret-secret-secret-secret-secret-12", 15, 7),
                new CatalogStudioProperties.Cors(List.of("http://localhost:5173")),
                new CatalogStudioProperties.Storage("local", "./target/test-uploads", "http://localhost"),
                new CatalogStudioProperties.Ai("mock", "", "mock", 30, "classpath:x", "gpt-image-1", "high", "high"),
                new CatalogStudioProperties.Upload(5, 1, List.of("image/jpeg")),
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
                new CatalogStudioProperties.Wholesale(
                        new CatalogStudioProperties.Wholesale.Gst(
                                provider, "", "", 0.72, "https://www.gstinapi.in", "")));
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
        assertThatThrownBy(() -> mock.verify("99ZZZZZ0000Z0ZZ", "Star"))
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
        assertThat(GstVerificationService.similarity("Star-Fashions LLP", "Star Fashions")).isEqualTo(1.0);
    }

    @Test
    void similarityIsHighForCloseNamesAndLowForDifferentOnes() {
        assertThat(GstVerificationService.similarity("Star Fashion", "Star Fashions Pvt Ltd")).isGreaterThan(0.72);
        assertThat(GstVerificationService.similarity("Royal Electronics", "Star Fashions")).isLessThan(0.5);
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

    @Test
    void gstinapiResponseRejectsInactiveAndFailed() throws Exception {
        var mapper = new ObjectMapper();
        assertThatThrownBy(() -> GstVerificationService.parseGstinApiResponse(mapper.readTree("""
                {"success":false,"message":"Invalid GSTIN"}
                """))).isInstanceOf(ApiException.class).hasMessageContaining("not found");
        assertThatThrownBy(() -> GstVerificationService.parseGstinApiResponse(mapper.readTree("""
                {"success":true,"legal_name":"ACME","trade_name":"ACME","status":"Cancelled"}
                """))).isInstanceOf(ApiException.class).hasMessageContaining("not active");
    }

    @Test
    void gstinapiProviderRequiresApiKey() {
        assertThatThrownBy(() -> service("gstinapi").verify(VALID_GSTIN, "Star Fashions"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("GSTINAPI_API_KEY");
    }

    @Test
    void preferredStoreNameUsesTradeNameThenLegalName() {
        assertThat(GstVerificationService.preferredStoreName("LEGAL CO", "TRADE SHOP")).isEqualTo("TRADE SHOP");
        assertThat(GstVerificationService.preferredStoreName("LEGAL CO", null)).isEqualTo("LEGAL CO");
        assertThat(GstVerificationService.preferredStoreName("LEGAL CO", "  ")).isEqualTo("LEGAL CO");
    }

    @Test
    void mockLookupPrefillsStoreNameFromTradeName() {
        var lookup = service("mock").lookup(VALID_GSTIN);
        assertThat(lookup.storeName()).isEqualTo(lookup.tradeName());
        assertThat(lookup.storeName()).isNotBlank();
    }
}
