package com.catalogstudio.subscription.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.subscription.dto.PaymentCheckoutResponse;
import com.catalogstudio.subscription.dto.PaymentStatusResponse;
import com.catalogstudio.subscription.dto.PriceBreakdown;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse.PaymentNotice;
import com.catalogstudio.subscription.entity.BillingSettings;
import com.catalogstudio.subscription.entity.PaymentTransaction;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionStatus;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionType;
import com.catalogstudio.subscription.entity.Subscription;
import com.catalogstudio.subscription.entity.Subscription.SubscriptionStatus;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.payment.CashfreeProperties;
import com.catalogstudio.subscription.payment.PaymentCheckoutCommand;
import com.catalogstudio.subscription.payment.PaymentGatewayService;
import com.catalogstudio.subscription.payment.PaymentProvider;
import com.catalogstudio.subscription.repository.PaymentTransactionRepository;
import com.catalogstudio.subscription.repository.SubscriptionPlanRepository;
import com.catalogstudio.subscription.repository.SubscriptionRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SubscriptionAccessService {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final BillingSettingsService billingSettingsService;
    private final PaymentGatewayService paymentGatewayService;
    private final PricingService pricingService;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final CatalogStudioProperties properties;
    private final CashfreeProperties cashfreeProperties;

    @Transactional
    public void ensureTrialOnLogin(User user) {
        if (user == null || user.isStaff()) {
            return;
        }
        Subscription subscription = latest(user.getId());
        if (subscription == null) {
            subscription = grantSignupTrial(user, null);
        }
        if (subscription.getStatus() == SubscriptionStatus.TRIAL && subscription.getTrialStartedAt() == null) {
            int days = billingSettingsService.current().getTrialDays();
            if (subscription.getStartDate() != null && subscription.getEndDate() != null) {
                days = (int) Math.max(days, ChronoUnit.DAYS.between(subscription.getStartDate(), subscription.getEndDate()) + 1);
            }
            startTrialClock(subscription, days);
        }
    }

    @Transactional
    public void assignSignupPlan(User user, Integer referralTrialDays) {
        if (latest(user.getId()) != null) {
            return;
        }
        grantSignupTrial(user, referralTrialDays);
    }

    @Transactional
    public SubscriptionStatusResponse statusOf(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
        return statusOf(user);
    }

    @Transactional
    public SubscriptionStatusResponse statusOf(User user) {
        BillingSettings billing = billingSettingsService.current();
        Subscription subscription = latest(user.getId());
        boolean admin = user.isStaff();
        Snapshot snapshot = snapshot(user, subscription, billing, admin);
        String pendingPlan = subscription == null || subscription.getPendingPlan() == null
                ? null
                : subscription.getPendingPlan().getName();
        return new SubscriptionStatusResponse(
                snapshot.plan(),
                snapshot.status(),
                snapshot.effectiveStatus(),
                snapshot.startDate(),
                snapshot.endDate(),
                snapshot.entitled(),
                snapshot.requiresRecharge(),
                snapshot.trialActive(),
                snapshot.daysRemaining(),
                snapshot.features(),
                user.getEmail(),
                billing.getTrialDays(),
                billing.getRechargeHeadline(),
                billing.getRechargeBody(),
                pendingPlan,
                new PaymentNotice(
                        billing.getPaymentProvider(),
                        billingSettingsService.resolvedWhatsappNumber(billing),
                        billing.getUpiId(),
                        billing.getPayeeName(),
                        billingSettingsService.scannerImageUrl(billing),
                        billing.getPaymentInstructions()
                )
        );
    }

    @Transactional(readOnly = true)
    public PriceBreakdown quote(Long userId, String planName, String promoCode) {
        userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
        return pricingService.quote(purchasablePlan(planName), promoCode);
    }

    @Transactional
    public PaymentCheckoutResponse checkout(
            Long userId, String planName, String promoCode, String customerPhone, String returnOrigin
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
        SubscriptionPlan plan = purchasablePlan(planName);
        BillingSettings billing = billingSettingsService.current();
        PriceBreakdown pricing = pricingService.quote(plan, promoCode);
        Subscription subscription = latest(userId);
        if (subscription != null) {
            subscription.setPendingPlan(plan);
            applyPendingPricing(subscription, pricing);
            if (!statusOf(user).accessEntitled()) {
                subscription.setStatus(SubscriptionStatus.PAYMENT_PENDING);
            }
        } else {
            subscription = subscriptionRepository.save(Subscription.builder()
                    .user(user)
                    .plan(plan)
                    .status(SubscriptionStatus.PAYMENT_PENDING)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().minusDays(1))
                    .pendingPlan(plan)
                    .build());
            applyPendingPricing(subscription, pricing);
        }

        String phone = resolveCustomerPhone(user, customerPhone);
        String orderId = "cs_" + user.getId() + "_" + plan.getName().toLowerCase() + "_"
                + Long.toString(System.currentTimeMillis(), 36);
        String origin = resolveReturnOrigin(returnOrigin);
        String returnUrl = origin + "/subscription/payment-result?order_id={order_id}";
        // Webhook must hit the public API host (www) regardless of which host the seller used.
        String notifyUrl = properties.cors().publicAppOrigin() + "/api/v1/payments/cashfree/webhook";

        PaymentTransaction pending = paymentTransactionService.record(
                user, plan, TransactionType.CHECKOUT, TransactionStatus.PENDING,
                cashfreeProperties.configured() ? "CASHFREE" : paymentGatewayService.active().name(),
                orderId,
                pricing.promoCode() == null
                        ? "Checkout started"
                        : "Checkout started with promo " + pricing.promoCode(),
                pricing);
        pending.setGatewayLinkId(orderId);
        paymentTransactionRepository.save(pending);

        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("uid", String.valueOf(user.getId()));
        notes.put("plan", plan.getName());
        if (pending.getUuid() != null) {
            notes.put("txn", pending.getUuid().toString().replace("-", "").substring(0, 16));
        }
        if (pricing.promoCode() != null) {
            notes.put("promo", pricing.promoCode());
        }

        PaymentProvider.CheckoutSession session = paymentGatewayService.checkout(new PaymentCheckoutCommand(
                user.getId(),
                plan.getName(),
                pricing.totalAmount(),
                "INR",
                StringUtils.hasText(user.getName()) ? user.getName().trim() : "Catalog Studio seller",
                user.getEmail(),
                phone,
                orderId,
                returnUrl,
                notifyUrl,
                notes
        ));

        String paymentSessionId = null;
        if (StringUtils.hasText(session.checkoutUrl()) && session.checkoutUrl().startsWith("cashfree-session:")) {
            paymentSessionId = session.checkoutUrl().substring("cashfree-session:".length());
        }

        pending.setProvider(session.provider());
        pending.setReference(session.sessionId());
        pending.setGatewayLinkId(session.sessionId());
        pending.setGatewayOrderId(session.sessionId());
        if (StringUtils.hasText(session.gatewayReference())
                && !session.gatewayReference().equals(session.sessionId())) {
            pending.setGatewayCfLinkId(session.gatewayReference());
        }
        pending.setNotes(pricing.promoCode() == null
                ? "Cashfree order checkout"
                : "Cashfree order checkout · promo " + pricing.promoCode());
        paymentTransactionRepository.save(pending);

        String notice = "CASHFREE".equalsIgnoreCase(session.provider())
                ? "You will be redirected to Cashfree to pay securely. Your plan activates automatically after payment."
                : "Complete payment using the instructions below.";
        return new PaymentCheckoutResponse(
                session.provider(),
                session.checkoutUrl(),
                session.sessionId(),
                paymentSessionId,
                plan.getName(),
                pricing.totalAmount(),
                plan.getBillingCycle(),
                user.getEmail(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                notice,
                StringUtils.hasText(billing.getPaymentInstructions())
                        ? billing.getPaymentInstructions()
                        : "Pay online with UPI, card, or netbanking. Access unlocks automatically after success.",
                pricing
        );
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse paymentStatus(Long userId, String orderId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
        if (!StringUtils.hasText(orderId)) {
            throw ApiException.badRequest("Missing payment order id");
        }
        PaymentTransaction txn = paymentTransactionRepository.findByGatewayLinkId(orderId.trim())
                .orElseThrow(() -> ApiException.notFound("Payment not found"));
        if (!txn.getUser().getId().equals(userId)) {
            throw ApiException.notFound("Payment not found");
        }
        boolean entitled = statusOf(user).accessEntitled();
        String status = txn.getStatus() == null ? "PENDING" : txn.getStatus().name();
        if (entitled || paymentTransactionRepository.existsByReferenceAndTypeAndStatus(
                orderId.trim(), TransactionType.ACTIVATION, TransactionStatus.SUCCESS)) {
            status = "SUCCESS";
        }
        String message = switch (status) {
            case "SUCCESS" -> "Payment received. Your plan is active.";
            case "FAILED", "EXPIRED" -> "Payment was not completed. You can try again from Subscription.";
            default -> "Waiting for Cashfree confirmation…";
        };
        return new PaymentStatusResponse(
                orderId.trim(),
                txn.getProvider(),
                status,
                txn.getPlanName(),
                txn.getAmount(),
                entitled || "SUCCESS".equals(status),
                message
        );
    }

    /** Legacy WhatsApp screenshot path — kept for admin/support only; UI no longer calls this. */
    @Transactional
    public SubscriptionStatusResponse markPaymentSent(Long userId, String planName) {
        throw ApiException.badRequest(
                "Manual QR / WhatsApp payment is disabled. Please pay online with Cashfree.");
    }

    private String resolveReturnOrigin(String requested) {
        if (StringUtils.hasText(requested)) {
            String cleaned = requested.trim().replaceAll("/+$", "");
            if (properties.cors().isAllowedAppOrigin(cleaned)) {
                return cleaned;
            }
        }
        return properties.cors().publicAppOrigin();
    }

    private String resolveCustomerPhone(User user, String override) {
        String raw = StringUtils.hasText(override) ? override : user.getMobile();
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        }
        if (digits.length() != 10) {
            throw ApiException.badRequest(
                    "Add a valid 10-digit Indian mobile number before paying (Settings → profile, or enter it at checkout).");
        }
        return digits;
    }

    public boolean isEntitled(User user) {
        return statusOf(user).accessEntitled();
    }

    public PriceBreakdown pendingBreakdown(Subscription subscription) {
        if (subscription == null || subscription.getPendingTotalAmount() == null) {
            return null;
        }
        return pendingAsBreakdown(subscription, billingSettingsService.current());
    }

    private void applyPendingPricing(Subscription subscription, PriceBreakdown pricing) {
        subscription.setPendingPromoCode(pricing.promoCode());
        subscription.setPendingBaseAmount(pricing.baseAmount());
        subscription.setPendingDiscountAmount(pricing.discountAmount());
        subscription.setPendingServiceCharge(pricing.serviceCharge());
        subscription.setPendingGstAmount(pricing.gstAmount());
        subscription.setPendingTotalAmount(pricing.totalAmount());
    }

    private PriceBreakdown pendingAsBreakdown(Subscription subscription, BillingSettings billing) {
        return new PriceBreakdown(
                nz(subscription.getPendingBaseAmount()),
                nz(subscription.getPendingDiscountAmount()),
                nz(subscription.getPendingServiceCharge()),
                nz(subscription.getPendingGstAmount()),
                nz(subscription.getPendingTotalAmount()),
                billing.getServiceChargePercent(),
                billing.getGstPercent(),
                subscription.getPendingPromoCode(),
                null,
                billing.getCompanyLegalName(),
                billing.getCompanyGstin(),
                billing.getParentCompanyName()
        );
    }

    private void clearPendingPricing(Subscription subscription) {
        subscription.setPendingPromoCode(null);
        subscription.setPendingBaseAmount(null);
        subscription.setPendingDiscountAmount(null);
        subscription.setPendingServiceCharge(null);
        subscription.setPendingGstAmount(null);
        subscription.setPendingTotalAmount(null);
    }

    /** Called from admin activation after success. */
    public void clearPendingAfterActivation(Subscription subscription) {
        if (subscription != null) {
            clearPendingPricing(subscription);
        }
    }

    private Subscription grantSignupTrial(User user, Integer referralTrialDays) {
        BillingSettings billing = billingSettingsService.current();
        int days = referralTrialDays != null && referralTrialDays > 0
                ? referralTrialDays
                : billing.getTrialDays();
        String planName = referralTrialDays != null && referralTrialDays > 0
                ? "PRO"
                : billing.getTrialPlan();
        SubscriptionPlan plan = planRepository.findByNameIgnoreCase(planName)
                .or(() -> planRepository.findByNameIgnoreCase("BASIC"))
                .or(() -> planRepository.findByNameIgnoreCase("FREE"))
                .or(() -> planRepository.findByNameIgnoreCase("PRO"))
                .orElseThrow(() -> ApiException.badRequest("Default plan is not configured"));
        SubscriptionStatus status;
        LocalDate start = LocalDate.now();
        LocalDate end;
        Instant trialStartedAt = null;
        if (days <= 0) {
            status = SubscriptionStatus.EXPIRED;
            end = start.minusDays(1);
        } else {
            status = SubscriptionStatus.TRIAL;
            end = trialEndDate(start, days);
        }
        return subscriptionRepository.save(Subscription.builder()
                .user(user)
                .plan(plan)
                .status(status)
                .startDate(start)
                .endDate(end)
                .trialStartedAt(trialStartedAt)
                .build());
    }

    private void startTrialClock(Subscription subscription, int trialDays) {
        LocalDate start = LocalDate.now();
        int days = Math.max(trialDays, 0);
        subscription.setStartDate(start);
        if (days <= 0) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscription.setEndDate(start.minusDays(1));
        } else {
            subscription.setEndDate(trialEndDate(start, days));
        }
        subscription.setTrialStartedAt(Instant.now());
    }

    private Snapshot snapshot(User user, Subscription subscription, BillingSettings billing, boolean admin) {
        if (admin) {
            String plan = subscription == null || subscription.getPlan() == null ? "BUSINESS" : subscription.getPlan().getName();
            Map<String, Object> features = subscription == null || subscription.getPlan() == null
                    ? Map.of()
                    : featuresOf(subscription.getPlan());
            String end = subscription == null || subscription.getEndDate() == null ? "" : subscription.getEndDate().toString();
            String start = subscription == null || subscription.getStartDate() == null ? "" : subscription.getStartDate().toString();
            String status = subscription == null ? "ACTIVE" : subscription.getStatus().name();
            return new Snapshot(plan, status, "ACTIVE", start, end, true, false, false, 0, features);
        }
        if (subscription == null || subscription.getPlan() == null) {
            return new Snapshot("NONE", "EXPIRED", "EXPIRED", "", "", false, true, false, 0, Map.of());
        }
        LocalDate today = LocalDate.now();
        LocalDate endDate = subscription.getEndDate();
        boolean windowOpen = endDate == null || !today.isAfter(endDate);
        SubscriptionStatus stored = subscription.getStatus();
        boolean trialActive = stored == SubscriptionStatus.TRIAL && windowOpen;
        boolean paidActive = stored == SubscriptionStatus.ACTIVE && windowOpen;
        boolean entitled = trialActive || paidActive;
        String effective = stored.name();
        if ((stored == SubscriptionStatus.TRIAL || stored == SubscriptionStatus.ACTIVE) && !windowOpen) {
            effective = "EXPIRED";
        }
        long daysRemaining = 0;
        if (entitled && endDate != null) {
            daysRemaining = Math.max(0, ChronoUnit.DAYS.between(today, endDate));
        }
        return new Snapshot(
                subscription.getPlan().getName(),
                stored.name(),
                effective,
                subscription.getStartDate() == null ? "" : subscription.getStartDate().toString(),
                endDate == null ? "" : endDate.toString(),
                entitled,
                !entitled,
                trialActive,
                daysRemaining,
                featuresOf(subscription.getPlan())
        );
    }

    private Subscription latest(Long userId) {
        return subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).orElse(null);
    }

    private SubscriptionPlan purchasablePlan(String planName) {
        if (!StringUtils.hasText(planName)) {
            throw ApiException.badRequest("Select a plan");
        }
        SubscriptionPlan plan = planRepository.findByNameIgnoreCase(planName.trim())
                .orElseThrow(() -> ApiException.notFound("Plan not found"));
        if (!"ACTIVE".equalsIgnoreCase(plan.getStatus())) {
            throw ApiException.badRequest("This plan is not available");
        }
        if ("FREE".equalsIgnoreCase(plan.getName()) || plan.getPrice() == null || plan.getPrice().signum() <= 0) {
            throw ApiException.badRequest("Choose a paid plan to recharge");
        }
        return plan;
    }

    private Map<String, Object> featuresOf(SubscriptionPlan plan) {
        Map<String, Object> features = plan.getFeaturesJson();
        return features == null ? Map.of() : new LinkedHashMap<>(features);
    }

    static LocalDate trialEndDate(LocalDate start, int trialDays) {
        return start.plusDays(Math.max(trialDays, 1) - 1L);
    }

    private String renderMessage(
            String template,
            String email,
            SubscriptionPlan plan,
            BillingSettings billing,
            String amount,
            String promoCode
    ) {
        String body = StringUtils.hasText(template)
                ? template
                : "Hello Catalog Studio, I have paid for the {{plan}} plan (₹{{amount}}). Registered email: {{email}}. Payment screenshot is attached.";
        return body
                .replace("{{plan}}", plan.getName())
                .replace("{{amount}}", amount == null ? "0" : amount)
                .replace("{{email}}", email)
                .replace("{{upi}}", billing.getUpiId() == null ? "" : billing.getUpiId())
                .replace("{{payee}}", billing.getPayeeName() == null ? "" : billing.getPayeeName())
                .replace("{{promo}}", promoCode == null ? "" : promoCode);
    }

    private static java.math.BigDecimal nz(java.math.BigDecimal value) {
        return value == null ? java.math.BigDecimal.ZERO : value;
    }

    private record Snapshot(
            String plan,
            String status,
            String effectiveStatus,
            String startDate,
            String endDate,
            boolean entitled,
            boolean requiresRecharge,
            boolean trialActive,
            long daysRemaining,
            Map<String, Object> features
    ) {}
}
