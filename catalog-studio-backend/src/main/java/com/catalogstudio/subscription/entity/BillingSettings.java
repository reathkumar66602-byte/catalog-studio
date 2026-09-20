package com.catalogstudio.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "billing_settings")
public class BillingSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(name = "settings_key", nullable = false, unique = true, length = 40)
    private String settingsKey = "default";

    @Builder.Default
    @Column(name = "trial_days", nullable = false)
    private int trialDays = 2;

    @Builder.Default
    @Column(name = "trial_plan", nullable = false, length = 40)
    private String trialPlan = "BASIC";

    @Builder.Default
    @Column(name = "whatsapp_number", nullable = false, length = 40)
    private String whatsappNumber = "919876543210";

    @Column(name = "whatsapp_message_template", nullable = false, columnDefinition = "text")
    private String whatsappMessageTemplate;

    @Builder.Default
    @Column(name = "upi_id", nullable = false, length = 120)
    private String upiId = "catalogstudio@upi";

    @Builder.Default
    @Column(name = "payee_name", nullable = false, length = 160)
    private String payeeName = "VISHAL KUMAR MISHRA";

    @Column(name = "qr_image_url", length = 500)
    private String qrImageUrl;

    @Builder.Default
    @Column(name = "payment_provider", nullable = false, length = 40)
    private String paymentProvider = "MANUAL";

    @Column(name = "payment_instructions", nullable = false, columnDefinition = "text")
    private String paymentInstructions;

    @Builder.Default
    @Column(name = "recharge_headline", nullable = false, length = 200)
    private String rechargeHeadline = "Recharge to keep using Catalog Studio";

    @Column(name = "recharge_body", nullable = false, columnDefinition = "text")
    private String rechargeBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (settingsKey == null) {
            settingsKey = "default";
        }
        createdAt = now;
        updatedAt = now;
        applyDefaults();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        applyDefaults();
    }

    private void applyDefaults() {
        if (trialPlan == null || trialPlan.isBlank()) {
            trialPlan = "BASIC";
        }
        if (whatsappNumber == null || whatsappNumber.isBlank()) {
            whatsappNumber = "919876543210";
        }
        if (upiId == null || upiId.isBlank()) {
            upiId = "catalogstudio@upi";
        }
        if (payeeName == null || payeeName.isBlank()) {
            payeeName = "VISHAL KUMAR MISHRA";
        }
        if (qrImageUrl == null || qrImageUrl.isBlank()) {
            qrImageUrl = "/payment-qr.jpg";
        }
        if (paymentProvider == null || paymentProvider.isBlank()) {
            paymentProvider = "MANUAL";
        }
        if (whatsappMessageTemplate == null || whatsappMessageTemplate.isBlank()) {
            whatsappMessageTemplate = "Hello Catalog Studio, I have paid for the {{plan}} plan (₹{{amount}})."
                    + " Registered email: {{email}}. Payment screenshot is attached.";
        }
        if (paymentInstructions == null || paymentInstructions.isBlank()) {
            paymentInstructions = "Scan this PhonePe QR, pay the plan amount, then send the payment screenshot on WhatsApp."
                    + " Mention your registered email ID in the same message so we can activate the correct account.";
        }
        if (rechargeHeadline == null || rechargeHeadline.isBlank()) {
            rechargeHeadline = "Recharge to keep using Catalog Studio";
        }
        if (rechargeBody == null || rechargeBody.isBlank()) {
            rechargeBody = "Your free trial has ended. Choose a plan, pay by UPI, and send the payment screenshot"
                    + " on WhatsApp with your registered email. Access is enabled after we confirm the payment.";
        }
        if (trialDays < 0) {
            trialDays = 0;
        }
    }
}
