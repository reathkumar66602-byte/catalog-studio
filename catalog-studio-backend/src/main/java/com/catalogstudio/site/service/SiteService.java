package com.catalogstudio.site.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.email.dto.MailIdentity;
import com.catalogstudio.email.service.MailIdentityService;
import com.catalogstudio.email.service.TemplatedEmailService;
import com.catalogstudio.site.dto.ClientPromoRequest;
import com.catalogstudio.site.dto.ClientStoreRequest;
import com.catalogstudio.site.dto.EnquiryRequest;
import com.catalogstudio.site.dto.EnquiryStatusRequest;
import com.catalogstudio.site.dto.SitePublicResponse;
import com.catalogstudio.site.dto.SitePublicResponse.AdminSiteBundle;
import com.catalogstudio.site.dto.SitePublicResponse.Branding;
import com.catalogstudio.site.dto.SitePublicResponse.ClientAdminResponse;
import com.catalogstudio.site.dto.SitePublicResponse.ClientPublic;
import com.catalogstudio.site.dto.SitePublicResponse.EnquiryAck;
import com.catalogstudio.site.dto.SitePublicResponse.EnquiryAdminResponse;
import com.catalogstudio.site.dto.SitePublicResponse.EnquiryForm;
import com.catalogstudio.site.dto.SitePublicResponse.PromoAdminResponse;
import com.catalogstudio.site.dto.SitePublicResponse.PromoPublic;
import com.catalogstudio.site.dto.SitePublicResponse.SiteSettingsResponse;
import com.catalogstudio.site.dto.SitePublicResponse.Support;
import com.catalogstudio.site.dto.SiteSettingsRequest;
import com.catalogstudio.site.entity.ClientPromoCode;
import com.catalogstudio.site.entity.ClientStore;
import com.catalogstudio.site.entity.Enquiry;
import com.catalogstudio.site.entity.SiteSettings;
import com.catalogstudio.site.repository.ClientPromoCodeRepository;
import com.catalogstudio.site.repository.ClientStoreRepository;
import com.catalogstudio.site.repository.EnquiryRepository;
import com.catalogstudio.site.repository.SiteSettingsRepository;
import com.catalogstudio.subscription.service.BillingSettingsService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class SiteService {

    private static final String DEFAULT_KEY = "default";
    private static final Set<String> DISCOUNT_TYPES = Set.of("PERCENT", "FIXED", "TRIAL");
    private static final Set<String> CLIENT_STATUSES = Set.of("ACTIVE", "HIDDEN");
    private static final Set<String> PROMO_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> ENQUIRY_STATUSES = Set.of("NEW", "READ", "REPLIED", "ARCHIVED");

    private final SiteSettingsRepository settingsRepository;
    private final ClientStoreRepository clientRepository;
    private final ClientPromoCodeRepository promoRepository;
    private final EnquiryRepository enquiryRepository;
    private final TemplatedEmailService templatedEmailService;
    private final MailIdentityService mailIdentityService;
    private final BillingSettingsService billingSettingsService;

    @Transactional(readOnly = true)
    public SitePublicResponse getPublicSite() {
        SiteSettings settings = currentSettings();
        ClientStore client = clientRepository.findFirstByFeaturedTrueAndStatusIgnoreCase("ACTIVE").orElse(null);
        List<PromoPublic> promos = List.of();
        if (client != null) {
            promos = promoRepository.findByClientAndStatusIgnoreCaseOrderByCreatedAtDesc(client, "ACTIVE").stream()
                    .filter(ClientPromoCode::isPubliclyActive)
                    .map(this::toPromoPublic)
                    .toList();
        }
        return new SitePublicResponse(
                toBranding(settings),
                new Support(settings.getSupportEmail(), settings.getSupportPhone(), billingSettingsService.resolvedWhatsappNumber()),
                new EnquiryForm(settings.isEnquiryEnabled(), settings.getEnquiryIntro(), settings.getEnquirySuccessMessage()),
                client == null ? null : toClientPublic(client),
                promos
        );
    }

    @Transactional
    public EnquiryAck submitEnquiry(EnquiryRequest request) {
        SiteSettings settings = currentSettings();
        if (!settings.isEnquiryEnabled()) {
            throw ApiException.badRequest("Enquiry form is currently disabled");
        }
        Enquiry enquiry = enquiryRepository.save(Enquiry.builder()
                .name(request.name().trim())
                .email(request.email().trim().toLowerCase(Locale.ROOT))
                .phone(trimToNull(request.phone()))
                .storeName(trimToNull(request.storeName()))
                .subject(trimToNull(request.subject()))
                .message(request.message().trim())
                .status("NEW")
                .build());
        Map<String, String> mailVars = enquiryMailVars(enquiry);
        String fallbackInbox = settings.getSupportEmail();
        queueEnquiryEmails(fallbackInbox, enquiry.getEmail(), mailVars);
        String message = StringUtils.hasText(settings.getEnquirySuccessMessage())
                ? settings.getEnquirySuccessMessage()
                : "Thanks. We received your enquiry.";
        return new EnquiryAck(enquiry.getUuid(), message);
    }

    @Transactional(readOnly = true)
    public AdminSiteBundle adminBundle() {
        return new AdminSiteBundle(
                toSettingsResponse(currentSettings()),
                clientRepository.findAll().stream().map(this::toClientAdmin).toList(),
                promoRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toPromoAdmin).toList(),
                enquiryRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 50)).getContent().stream()
                        .map(this::toEnquiryAdmin)
                        .toList()
        );
    }

    @Transactional
    public SiteSettingsResponse updateSettings(SiteSettingsRequest request) {
        SiteSettings settings = currentSettings();
        settings.setSiteName(request.siteName().trim());
        settings.setTagline(trimToNull(request.tagline()));
        settings.setHeroTitle(request.heroTitle().trim());
        settings.setHeroSubtitle(trimToNull(request.heroSubtitle()));
        settings.setLogoUrl(trimToNull(request.logoUrl()));
        settings.setPrimaryColor(trimToNull(request.primaryColor()));
        settings.setAccentColor(trimToNull(request.accentColor()));
        settings.setHeroBackground(trimToNull(request.heroBackground()));
        settings.setFooterText(trimToNull(request.footerText()));
        settings.setSupportEmail(request.supportEmail().trim().toLowerCase(Locale.ROOT));
        settings.setMailFromEmail(request.mailFromEmail() == null || request.mailFromEmail().isBlank()
                ? settings.getSupportEmail()
                : request.mailFromEmail().trim().toLowerCase(Locale.ROOT));
        settings.setMailFromName(trimToNull(request.mailFromName()) == null
                ? settings.getSiteName()
                : request.mailFromName().trim());
        settings.setSupportPhone(trimToNull(request.supportPhone()));
        settings.setEnquiryEnabled(request.enquiryEnabled());
        settings.setEnquiryIntro(trimToNull(request.enquiryIntro()));
        settings.setEnquirySuccessMessage(trimToNull(request.enquirySuccessMessage()));
        return toSettingsResponse(settings);
    }

    @Transactional
    public ClientAdminResponse upsertClient(UUID id, ClientStoreRequest request) {
        String slug = normalizeSlug(request.slug());
        ClientStore client = id == null
                ? new ClientStore()
                : clientRepository.findByUuid(id).orElseThrow(() -> ApiException.notFound("Client not found"));
        if (clientRepository.existsBySlugIgnoreCaseAndIdNot(slug, client.getId() == null ? -1L : client.getId())) {
            throw ApiException.conflict("A client with this slug already exists");
        }
        client.setStoreName(request.storeName().trim());
        client.setSlug(slug);
        client.setOwnerName(trimToNull(request.ownerName()));
        client.setEmail(trimToNull(request.email()));
        client.setPhone(trimToNull(request.phone()));
        client.setAddress(trimToNull(request.address()));
        client.setWebsiteUrl(trimToNull(request.websiteUrl()));
        client.setLogoUrl(trimToNull(request.logoUrl()));
        client.setTagline(trimToNull(request.tagline()));
        client.setAbout(trimToNull(request.about()));
        client.setBrandingJson(request.branding() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.branding()));
        client.setFeatured(request.featured());
        client.setStatus(normalizeEnum(request.status(), CLIENT_STATUSES, "ACTIVE"));
        ClientStore saved = clientRepository.save(client);
        if (saved.isFeatured()) {
            clientRepository.findAll().stream()
                    .filter(other -> !other.getId().equals(saved.getId()) && other.isFeatured())
                    .forEach(other -> other.setFeatured(false));
        }
        return toClientAdmin(saved);
    }

    @Transactional
    public PromoAdminResponse upsertPromo(UUID id, ClientPromoRequest request) {
        if (request.clientId() == null) {
            throw ApiException.badRequest("Client is required");
        }
        ClientStore client = clientRepository.findByUuid(request.clientId())
                .orElseThrow(() -> ApiException.notFound("Client not found"));
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        String discountType = normalizeEnum(request.discountType(), DISCOUNT_TYPES, null);
        if (discountType == null) {
            throw ApiException.badRequest("Discount type must be PERCENT, FIXED, or TRIAL");
        }
        ClientPromoCode promo = id == null
                ? new ClientPromoCode()
                : promoRepository.findByUuid(id).orElseThrow(() -> ApiException.notFound("Promo code not found"));
        if (promoRepository.existsByCodeIgnoreCaseAndIdNot(code, promo.getId() == null ? -1L : promo.getId())) {
            throw ApiException.conflict("This promo code already exists");
        }
        promo.setClient(client);
        promo.setCode(code);
        promo.setHeadline(trimToNull(request.headline()));
        promo.setDescription(trimToNull(request.description()));
        promo.setDiscountType(discountType);
        promo.setDiscountValue(request.discountValue() == null ? BigDecimal.ZERO : request.discountValue());
        promo.setTrialDays(Math.max(request.trialDays(), 0));
        promo.setValidFrom(request.validFrom());
        promo.setValidUntil(request.validUntil());
        promo.setStatus(normalizeEnum(request.status(), PROMO_STATUSES, "ACTIVE"));
        return toPromoAdmin(promoRepository.save(promo));
    }

    @Transactional
    public void deletePromo(UUID id) {
        ClientPromoCode promo = promoRepository.findByUuid(id).orElseThrow(() -> ApiException.notFound("Promo code not found"));
        promoRepository.delete(promo);
    }

    @Transactional
    public EnquiryAdminResponse updateEnquiryStatus(UUID id, EnquiryStatusRequest request) {
        Enquiry enquiry = enquiryRepository.findByUuid(id).orElseThrow(() -> ApiException.notFound("Enquiry not found"));
        String status = normalizeEnum(request.status(), ENQUIRY_STATUSES, null);
        if (status == null) {
            throw ApiException.badRequest("Invalid enquiry status");
        }
        enquiry.setStatus(status);
        return toEnquiryAdmin(enquiry);
    }

    private SiteSettings currentSettings() {
        return settingsRepository.findBySiteKey(DEFAULT_KEY).orElseGet(() -> settingsRepository.save(SiteSettings.builder()
                .siteKey(DEFAULT_KEY)
                .siteName("Catalog Studio")
                .tagline("Seller tools for listings, labels, and growth")
                .heroTitle("Crop labels, estimate profit, and fill listings faster")
                .heroSubtitle("Catalog Studio crops Flipkart and Meesho shipping labels in your browser, estimates Meesho margins, and pairs a Chrome extension that fills GST and HSN. You always submit the listing yourself.")
                .logoUrl("/logo.svg")
                .primaryColor("#0f766e")
                .accentColor("#38bdf8")
                .heroBackground("#07111f")
                .footerText("Catalog Studio seller tools.")
                .supportEmail("support@catalogstudio.in")
                .mailFromEmail("support@catalogstudio.in")
                .mailFromName("Catalog Studio")
                .supportPhone("+91 98765 43210")
                .enquiryEnabled(true)
                .enquiryIntro("Send a question about crop, calculator, or the extension.")
                .enquirySuccessMessage("Thanks. We received your enquiry.")
                .build()));
    }

    private void queueEnquiryEmails(String fallbackInbox, String visitorEmail, Map<String, String> vars) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchEnquiryEmails(fallbackInbox, visitorEmail, vars);
                }
            });
            return;
        }
        dispatchEnquiryEmails(fallbackInbox, visitorEmail, vars);
    }

    private Map<String, String> enquiryMailVars(Enquiry enquiry) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("name", enquiry.getName());
        vars.put("email", enquiry.getEmail());
        vars.put("phone", blank(enquiry.getPhone()));
        vars.put("storeName", blank(enquiry.getStoreName()));
        vars.put("subject", blank(enquiry.getSubject()));
        vars.put("message", enquiry.getMessage());
        return vars;
    }

    private void dispatchEnquiryEmails(String fallbackInbox, String visitorEmail, Map<String, String> vars) {
        try {
            MailIdentity identity = mailIdentityService.current();
            String support = StringUtils.hasText(identity.supportEmail()) ? identity.supportEmail() : fallbackInbox;
            if (!StringUtils.hasText(support)) {
                log.error("Enquiry stored but no support inbox is configured");
                return;
            }
            boolean notified = templatedEmailService.send("enquiry-received", support, visitorEmail, vars);
            if (!notified) {
                log.error("Enquiry support email was not delivered to {}", support);
            } else {
                log.info("Enquiry support email sent to {}", support);
            }
            if (StringUtils.hasText(visitorEmail) && !visitorEmail.equalsIgnoreCase(support)) {
                boolean acknowledged = templatedEmailService.send("enquiry-ack", visitorEmail, support, vars);
                if (!acknowledged) {
                    log.error("Enquiry acknowledgement was not delivered to {}", visitorEmail);
                } else {
                    log.info("Enquiry acknowledgement sent to {}", visitorEmail);
                }
            }
        } catch (Exception ex) {
            log.error("Could not email enquiry: {}", ex.getMessage());
        }
    }

    private Branding toBranding(SiteSettings settings) {
        return new Branding(
                settings.getSiteName(),
                settings.getTagline(),
                settings.getHeroTitle(),
                settings.getHeroSubtitle(),
                settings.getLogoUrl(),
                settings.getPrimaryColor(),
                settings.getAccentColor(),
                settings.getHeroBackground(),
                settings.getFooterText()
        );
    }

    private SiteSettingsResponse toSettingsResponse(SiteSettings settings) {
        return new SiteSettingsResponse(
                settings.getSiteName(),
                settings.getTagline(),
                settings.getHeroTitle(),
                settings.getHeroSubtitle(),
                settings.getLogoUrl(),
                settings.getPrimaryColor(),
                settings.getAccentColor(),
                settings.getHeroBackground(),
                settings.getFooterText(),
                settings.getSupportEmail(),
                settings.getMailFromEmail(),
                settings.getMailFromName(),
                settings.getSupportPhone(),
                settings.isEnquiryEnabled(),
                settings.getEnquiryIntro(),
                settings.getEnquirySuccessMessage()
        );
    }

    private ClientPublic toClientPublic(ClientStore client) {
        return new ClientPublic(
                client.getUuid(),
                client.getStoreName(),
                client.getSlug(),
                client.getOwnerName(),
                client.getEmail(),
                client.getPhone(),
                client.getAddress(),
                client.getWebsiteUrl(),
                client.getLogoUrl(),
                client.getTagline(),
                client.getAbout(),
                brandingOrEmpty(client)
        );
    }

    private ClientAdminResponse toClientAdmin(ClientStore client) {
        return new ClientAdminResponse(
                client.getUuid(),
                client.getStoreName(),
                client.getSlug(),
                client.getOwnerName(),
                client.getEmail(),
                client.getPhone(),
                client.getAddress(),
                client.getWebsiteUrl(),
                client.getLogoUrl(),
                client.getTagline(),
                client.getAbout(),
                brandingOrEmpty(client),
                client.isFeatured(),
                client.getStatus()
        );
    }

    private PromoPublic toPromoPublic(ClientPromoCode promo) {
        return new PromoPublic(
                promo.getCode(),
                promo.getHeadline(),
                promo.getDescription(),
                promo.getDiscountType(),
                promo.getDiscountValue(),
                promo.getTrialDays(),
                promo.getValidUntil()
        );
    }

    private PromoAdminResponse toPromoAdmin(ClientPromoCode promo) {
        ClientStore client = promo.getClient();
        return new PromoAdminResponse(
                promo.getUuid(),
                client == null ? null : client.getUuid(),
                client == null ? null : client.getStoreName(),
                promo.getCode(),
                promo.getHeadline(),
                promo.getDescription(),
                promo.getDiscountType(),
                promo.getDiscountValue(),
                promo.getTrialDays(),
                promo.getValidFrom(),
                promo.getValidUntil(),
                promo.getStatus()
        );
    }

    private EnquiryAdminResponse toEnquiryAdmin(Enquiry enquiry) {
        return new EnquiryAdminResponse(
                enquiry.getUuid(),
                enquiry.getName(),
                enquiry.getEmail(),
                enquiry.getPhone(),
                enquiry.getStoreName(),
                enquiry.getSubject(),
                enquiry.getMessage(),
                enquiry.getStatus(),
                enquiry.getCreatedAt()
        );
    }

    private static Map<String, Object> brandingOrEmpty(ClientStore client) {
        return client.getBrandingJson() == null ? Map.of() : client.getBrandingJson();
    }

    private static String normalizeSlug(String slug) {
        String value = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-");
        value = value.replaceAll("^-+|-+$", "");
        if (!StringUtils.hasText(value)) {
            throw ApiException.badRequest("Enter a valid store slug");
        }
        return value;
    }

    private static String normalizeEnum(String value, Set<String> allowed, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(upper) ? upper : fallback;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String blank(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
