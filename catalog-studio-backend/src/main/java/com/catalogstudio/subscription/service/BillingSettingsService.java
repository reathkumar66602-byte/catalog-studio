package com.catalogstudio.subscription.service;

import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.subscription.dto.BillingSettingsRequest;
import com.catalogstudio.subscription.dto.BillingSettingsResponse;
import com.catalogstudio.subscription.entity.BillingSettings;
import com.catalogstudio.subscription.repository.BillingSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BillingSettingsService {

    public static final String DEFAULT_KEY = "default";
    public static final String DEFAULT_SCANNER = "/payment-qr.jpg";

    private final BillingSettingsRepository repository;
    private final CatalogStudioProperties properties;

    @Transactional
    public BillingSettings current() {
        return repository.findBySettingsKey(DEFAULT_KEY).orElseGet(this::createDefault);
    }

    @Transactional
    public BillingSettingsResponse view() {
        return toView(current());
    }

    @Transactional
    public BillingSettingsResponse update(BillingSettingsRequest request) {
        BillingSettings settings = current();
        if (request.trialDays() != null) {
            settings.setTrialDays(Math.max(request.trialDays(), 0));
        }
        if (StringUtils.hasText(request.trialPlan())) {
            settings.setTrialPlan(request.trialPlan().trim().toUpperCase());
        }
        if (StringUtils.hasText(request.whatsappNumber())) {
            settings.setWhatsappNumber(digits(request.whatsappNumber()));
        }
        if (request.whatsappMessageTemplate() != null && StringUtils.hasText(request.whatsappMessageTemplate())) {
            settings.setWhatsappMessageTemplate(request.whatsappMessageTemplate().trim());
        }
        if (StringUtils.hasText(request.upiId())) {
            settings.setUpiId(request.upiId().trim());
        }
        if (StringUtils.hasText(request.payeeName())) {
            settings.setPayeeName(request.payeeName().trim());
        }
        if (request.qrImageUrl() != null) {
            settings.setQrImageUrl(StringUtils.hasText(request.qrImageUrl())
                    ? request.qrImageUrl().trim()
                    : DEFAULT_SCANNER);
        }
        if (StringUtils.hasText(request.paymentProvider())) {
            settings.setPaymentProvider(request.paymentProvider().trim().toUpperCase());
        }
        if (StringUtils.hasText(request.paymentInstructions())) {
            settings.setPaymentInstructions(request.paymentInstructions().trim());
        }
        if (StringUtils.hasText(request.rechargeHeadline())) {
            settings.setRechargeHeadline(request.rechargeHeadline().trim());
        }
        if (StringUtils.hasText(request.rechargeBody())) {
            settings.setRechargeBody(request.rechargeBody().trim());
        }
        if (StringUtils.hasText(fromProperty())) {
            settings.setWhatsappNumber(fromProperty());
        }
        return toView(settings);
    }

    public String resolvedWhatsappNumber() {
        return resolvedWhatsappNumber(null);
    }

    public String resolvedWhatsappNumber(BillingSettings settings) {
        String fromProp = fromProperty();
        if (StringUtils.hasText(fromProp)) {
            return fromProp;
        }
        return digits(settings == null ? null : settings.getWhatsappNumber());
    }

    private String fromProperty() {
        if (properties == null || properties.billing() == null) {
            return "";
        }
        return properties.billing().resolvedWhatsappNumber();
    }

    public static String digits(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("00")) {
            cleaned = cleaned.substring(2);
        }
        if (cleaned.length() == 10) {
            return "91" + cleaned;
        }
        return cleaned;
    }

    private BillingSettings createDefault() {
        return repository.save(BillingSettings.builder()
                .settingsKey(DEFAULT_KEY)
                .trialDays(2)
                .trialPlan("BASIC")
                .whatsappNumber(StringUtils.hasText(fromProperty()) ? fromProperty() : "919560111849")
                .payeeName("VISHAL KUMAR MISHRA")
                .qrImageUrl(DEFAULT_SCANNER)
                .paymentProvider("MANUAL")
                .build());
    }

    public String scannerImageUrl(BillingSettings settings) {
        if (settings != null && StringUtils.hasText(settings.getQrImageUrl())) {
            return settings.getQrImageUrl();
        }
        return DEFAULT_SCANNER;
    }

    private BillingSettingsResponse toView(BillingSettings settings) {
        return new BillingSettingsResponse(
                settings.getTrialDays(),
                settings.getTrialPlan(),
                resolvedWhatsappNumber(settings),
                settings.getWhatsappMessageTemplate(),
                settings.getUpiId(),
                settings.getPayeeName(),
                settings.getQrImageUrl() == null || settings.getQrImageUrl().isBlank()
                        ? DEFAULT_SCANNER
                        : settings.getQrImageUrl(),
                settings.getPaymentProvider(),
                settings.getPaymentInstructions(),
                settings.getRechargeHeadline(),
                settings.getRechargeBody()
        );
    }
}
