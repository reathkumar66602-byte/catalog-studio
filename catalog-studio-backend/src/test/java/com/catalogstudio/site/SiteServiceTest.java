package com.catalogstudio.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.email.dto.MailIdentity;
import com.catalogstudio.email.service.MailIdentityService;
import com.catalogstudio.email.service.TemplatedEmailService;
import com.catalogstudio.site.dto.ClientPromoRequest;
import com.catalogstudio.site.dto.EnquiryRequest;
import com.catalogstudio.site.dto.SitePublicResponse;
import com.catalogstudio.site.dto.SiteSettingsRequest;
import com.catalogstudio.site.entity.ClientPromoCode;
import com.catalogstudio.site.entity.ClientStore;
import com.catalogstudio.site.entity.Enquiry;
import com.catalogstudio.site.entity.SiteSettings;
import com.catalogstudio.site.repository.ClientPromoCodeRepository;
import com.catalogstudio.site.repository.ClientStoreRepository;
import com.catalogstudio.site.repository.EnquiryRepository;
import com.catalogstudio.site.repository.SiteSettingsRepository;
import com.catalogstudio.site.service.SiteService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SiteServiceTest {

    @Mock SiteSettingsRepository settingsRepository;
    @Mock ClientStoreRepository clientRepository;
    @Mock ClientPromoCodeRepository promoRepository;
    @Mock EnquiryRepository enquiryRepository;
    @Mock TemplatedEmailService templatedEmailService;
    @Mock MailIdentityService mailIdentityService;
    @InjectMocks SiteService siteService;

    private SiteSettings settings;
    private ClientStore krishna;

    @BeforeEach
    void setUp() {
        settings = SiteSettings.builder()
                .siteKey("default")
                .siteName("Catalog Studio")
                .heroTitle("Elevate Your Online Business With Intelligent Image Editing Tools!")
                .supportEmail("support@catalogstudio.local")
                .enquiryEnabled(true)
                .enquirySuccessMessage("Thanks from Krishna Store")
                .build();
        krishna = ClientStore.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .storeName("Krishna Store")
                .slug("krishna-store")
                .featured(true)
                .status("ACTIVE")
                .brandingJson(Map.of("primaryColor", "#0f766e"))
                .build();
    }

    @Test
    void publicSiteIncludesKrishnaStoreAndPromo() {
        ClientPromoCode promo = ClientPromoCode.builder()
                .code("KRISHNA10")
                .headline("Krishna Store seller offer")
                .description("10% off")
                .discountType("PERCENT")
                .discountValue(BigDecimal.TEN)
                .status("ACTIVE")
                .validUntil(LocalDate.now().plusYears(1))
                .client(krishna)
                .build();
        when(settingsRepository.findBySiteKey("default")).thenReturn(Optional.of(settings));
        when(clientRepository.findFirstByFeaturedTrueAndStatusIgnoreCase("ACTIVE")).thenReturn(Optional.of(krishna));
        when(promoRepository.findByClientAndStatusIgnoreCaseOrderByCreatedAtDesc(krishna, "ACTIVE"))
                .thenReturn(List.of(promo));

        SitePublicResponse site = siteService.getPublicSite();

        assertThat(site.branding().siteName()).isEqualTo("Catalog Studio");
        assertThat(site.client().storeName()).isEqualTo("Krishna Store");
        assertThat(site.support().email()).isEqualTo("support@catalogstudio.local");
        assertThat(site.promoCodes()).extracting(SitePublicResponse.PromoPublic::code).containsExactly("KRISHNA10");
    }

    @Test
    void enquiryIsStoredAndEmailedToSupport() {
        when(settingsRepository.findBySiteKey("default")).thenReturn(Optional.of(settings));
        when(mailIdentityService.current()).thenReturn(new MailIdentity(
                "support@catalogstudio.local", "Catalog Studio", "support@catalogstudio.local"));
        when(enquiryRepository.save(any(Enquiry.class))).thenAnswer(inv -> {
            Enquiry enquiry = inv.getArgument(0);
            enquiry.setUuid(UUID.randomUUID());
            return enquiry;
        });

        when(templatedEmailService.send(anyString(), anyString(), anyString(), any())).thenReturn(true);

        var ack = siteService.submitEnquiry(new EnquiryRequest(
                "Asha", "asha@example.com", "9999999999", "Krishna Store", "Label crop", "Need help cropping Meesho labels"));

        assertThat(ack.message()).isEqualTo("Thanks from Krishna Store");
        ArgumentCaptor<Enquiry> captor = ArgumentCaptor.forClass(Enquiry.class);
        verify(enquiryRepository).save(captor.capture());
        assertThat(captor.getValue().getStoreName()).isEqualTo("Krishna Store");
        verify(templatedEmailService).send(eq("enquiry-received"), eq("support@catalogstudio.local"), eq("asha@example.com"), any());
        verify(templatedEmailService).send(eq("enquiry-ack"), eq("asha@example.com"), eq("support@catalogstudio.local"), any());
    }

    @Test
    void enquiryRejectedWhenFormDisabled() {
        settings.setEnquiryEnabled(false);
        when(settingsRepository.findBySiteKey("default")).thenReturn(Optional.of(settings));
        assertThatThrownBy(() -> siteService.submitEnquiry(new EnquiryRequest(
                        "Asha", "asha@example.com", null, null, null, "Hello")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void adminCanUpdateSupportEmailAndBranding() {
        when(settingsRepository.findBySiteKey("default")).thenReturn(Optional.of(settings));
        var updated = siteService.updateSettings(new SiteSettingsRequest(
                "Catalog Studio",
                "New tagline",
                "New hero",
                "Subtitle",
                "/logo.svg",
                "#111111",
                "#22d3ee",
                "#020617",
                "Footer",
                "help@krishnastore.local",
                "support@catalogstudio.in",
                "Catalog Studio",
                "9000000000",
                true,
                "Ask us",
                "Got it"));
        assertThat(updated.supportEmail()).isEqualTo("help@krishnastore.local");
        assertThat(updated.mailFromEmail()).isEqualTo("support@catalogstudio.in");
        assertThat(updated.mailFromName()).isEqualTo("Catalog Studio");
        assertThat(updated.primaryColor()).isEqualTo("#111111");
        assertThat(updated.tagline()).isEqualTo("New tagline");
    }

    @Test
    void promoCodeMustBeUnique() {
        when(clientRepository.findByUuid(krishna.getUuid())).thenReturn(Optional.of(krishna));
        when(promoRepository.existsByCodeIgnoreCaseAndIdNot("KRISHNA10", -1L)).thenReturn(true);
        assertThatThrownBy(() -> siteService.upsertPromo(null, new ClientPromoRequest(
                        krishna.getUuid(),
                        "krishna10",
                        "Offer",
                        "10% off",
                        "PERCENT",
                        BigDecimal.TEN,
                        0,
                        null,
                        null,
                        "ACTIVE")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void adminBundleLoadsSettingsClientsPromosAndEnquiries() {
        when(settingsRepository.findBySiteKey("default")).thenReturn(Optional.of(settings));
        when(clientRepository.findAll()).thenReturn(List.of(krishna));
        when(promoRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(enquiryRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var bundle = siteService.adminBundle();
        assertThat(bundle.settings().siteName()).isEqualTo("Catalog Studio");
        assertThat(bundle.clients()).hasSize(1);
    }
}
