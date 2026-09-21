package com.catalogstudio.subscription.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.subscription.dto.PaymentCheckoutResponse;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse.PaymentNotice;
import com.catalogstudio.subscription.entity.BillingSettings;
import com.catalogstudio.subscription.entity.Subscription;
import com.catalogstudio.subscription.entity.Subscription.SubscriptionStatus;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.payment.PaymentGatewayService;
import com.catalogstudio.subscription.payment.PaymentProvider;
import com.catalogstudio.subscription.repository.SubscriptionPlanRepository;
import com.catalogstudio.subscription.repository.SubscriptionRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    @Transactional
    public void ensureTrialOnLogin(User user) {
        if (user == null || user.getRole() == User.Role.ADMIN) {
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
        boolean admin = user.getRole() == User.Role.ADMIN;
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

    @Transactional
    public PaymentCheckoutResponse checkout(Long userId, String planName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
        SubscriptionPlan plan = purchasablePlan(planName);
        BillingSettings billing = billingSettingsService.current();
        Subscription subscription = latest(userId);
        if (subscription != null) {
            subscription.setPendingPlan(plan);
            if (!statusOf(user).accessEntitled()) {
                subscription.setStatus(SubscriptionStatus.PAYMENT_PENDING);
            }
        }
        PaymentProvider.CheckoutSession session = paymentGatewayService.checkout(userId, plan.getName());
        String message = renderMessage(billing.getWhatsappMessageTemplate(), user.getEmail(), plan, billing);
        String digits = billingSettingsService.resolvedWhatsappNumber(billing);
        String whatsappUrl = "https://wa.me/" + digits + "?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);
        String scannerUrl = billingSettingsService.scannerImageUrl(billing);
        String notice = "Send the payment screenshot on WhatsApp and mention your registered email ID ("
                + user.getEmail() + "). We activate the account only after this confirmation.";
        return new PaymentCheckoutResponse(
                session.provider(),
                session.checkoutUrl(),
                session.sessionId(),
                plan.getName(),
                plan.getPrice(),
                plan.getBillingCycle(),
                user.getEmail(),
                billing.getUpiId(),
                billing.getPayeeName(),
                scannerUrl,
                null,
                digits,
                whatsappUrl,
                message,
                notice,
                billing.getPaymentInstructions()
        );
    }

    @Transactional
    public SubscriptionStatusResponse markPaymentSent(Long userId, String planName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
        SubscriptionPlan plan = purchasablePlan(planName);
        Subscription subscription = latest(userId);
        if (subscription == null) {
            subscription = subscriptionRepository.save(Subscription.builder()
                    .user(user)
                    .plan(plan)
                    .status(SubscriptionStatus.PAYMENT_PENDING)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().minusDays(1))
                    .pendingPlan(plan)
                    .build());
        } else {
            subscription.setPendingPlan(plan);
            if (!statusOf(user).accessEntitled()) {
                subscription.setStatus(SubscriptionStatus.PAYMENT_PENDING);
            }
        }
        return statusOf(user);
    }

    public boolean isEntitled(User user) {
        return statusOf(user).accessEntitled();
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

    private String renderMessage(String template, String email, SubscriptionPlan plan, BillingSettings billing) {
        String body = StringUtils.hasText(template)
                ? template
                : "Hello Catalog Studio, I have paid for the {{plan}} plan (₹{{amount}}). Registered email: {{email}}. Payment screenshot is attached.";
        String amount = plan.getPrice() == null ? "0" : plan.getPrice().stripTrailingZeros().toPlainString();
        return body
                .replace("{{plan}}", plan.getName())
                .replace("{{amount}}", amount)
                .replace("{{email}}", email)
                .replace("{{upi}}", billing.getUpiId() == null ? "" : billing.getUpiId())
                .replace("{{payee}}", billing.getPayeeName() == null ? "" : billing.getPayeeName());
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
