package com.catalogstudio.subscription.service;

import com.catalogstudio.audit.service.AuditService;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.email.service.TemplatedEmailService;
import com.catalogstudio.subscription.dto.PriceBreakdown;
import com.catalogstudio.subscription.entity.PaymentTransaction;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionStatus;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionType;
import com.catalogstudio.subscription.entity.Subscription;
import com.catalogstudio.subscription.entity.Subscription.SubscriptionStatus;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.repository.SubscriptionPlanRepository;
import com.catalogstudio.subscription.repository.SubscriptionRepository;
import com.catalogstudio.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Single activation path used by admin tools and Cashfree webhooks so paid users unlock consistently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionActivationService {

    private static final DateTimeFormatter HUMAN_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final PricingService pricingService;
    private final PaymentTransactionService paymentTransactionService;
    private final SubscriptionAccessService subscriptionAccessService;
    private final AuditService auditService;
    private final TemplatedEmailService templatedEmailService;
    private final CatalogStudioProperties properties;

    @Transactional
    public Subscription activatePaid(
            User user,
            SubscriptionPlan plan,
            String provider,
            String reference,
            String notes,
            PriceBreakdown pricing,
            User actorOrNull
    ) {
        if (user == null || plan == null) {
            throw ApiException.badRequest("User and plan are required to activate");
        }
        Subscription subscription = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseGet(() -> subscriptionRepository.save(Subscription.builder()
                        .user(user)
                        .plan(plan)
                        .status(SubscriptionStatus.PAYMENT_PENDING)
                        .startDate(LocalDate.now())
                        .endDate(LocalDate.now().minusDays(1))
                        .pendingPlan(plan)
                        .build()));

        PriceBreakdown resolved = pricing;
        if (resolved == null) {
            resolved = subscriptionAccessService.pendingBreakdown(subscription);
        }
        if (resolved == null) {
            try {
                resolved = pricingService.quote(plan, subscription.getPendingPromoCode());
            } catch (Exception ex) {
                BigDecimal price = plan.getPrice() == null ? BigDecimal.ZERO : plan.getPrice();
                resolved = new PriceBreakdown(
                        price, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, price,
                        BigDecimal.ZERO, BigDecimal.ZERO, subscription.getPendingPromoCode(),
                        null, null, null, null);
            }
        }

        LocalDate start = LocalDate.now();
        subscription.setPlan(plan);
        subscription.setPendingPlan(null);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(start);
        subscription.setEndDate(endDateFor(plan, start));

        String promoNote = StringUtils.hasText(resolved.promoCode())
                ? " · promo " + resolved.promoCode() + " applied (−₹" + resolved.discountAmount() + ")"
                : "";
        String finalNotes = StringUtils.hasText(notes)
                ? notes.trim()
                : "Activated after online payment" + promoNote;
        String finalReference = StringUtils.hasText(reference) ? reference.trim() : provider + ":" + user.getEmail();

        PaymentTransaction activation = paymentTransactionService.record(
                user, plan, TransactionType.ACTIVATION, TransactionStatus.SUCCESS,
                provider, finalReference, finalNotes, resolved);

        if (StringUtils.hasText(resolved.promoCode())) {
            pricingService.consumePromo(resolved.promoCode());
        }
        subscriptionAccessService.clearPendingAfterActivation(subscription);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("plan", plan.getName());
        audit.put("reference", finalReference);
        audit.put("provider", provider);
        audit.put("promoCode", resolved.promoCode() == null ? "" : resolved.promoCode());
        audit.put("totalAmount", resolved.totalAmount() == null ? "" : resolved.totalAmount().toPlainString());
        audit.put("transactionId", activation.getUuid() == null ? "" : activation.getUuid().toString());
        User actor = actorOrNull == null ? user : actorOrNull;
        auditService.log(actor, "SUBSCRIPTION_ACTIVATED", "USER", user.getUuid().toString(), null, audit);
        sendActivationEmail(user, plan, subscription.getStartDate(), subscription.getEndDate(), finalReference);
        log.info("Subscription activated user={} plan={} provider={} ref={}",
                user.getEmail(), plan.getName(), provider, finalReference);
        return subscription;
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan requirePlan(String planName) {
        return planRepository.findByNameIgnoreCase(planName == null ? "" : planName.trim())
                .orElseThrow(() -> ApiException.notFound("Plan not found"));
    }

    public LocalDate endDateFor(SubscriptionPlan plan, LocalDate start) {
        String cycle = plan.getBillingCycle() == null ? "MONTHLY" : plan.getBillingCycle().toUpperCase(Locale.ROOT);
        return switch (cycle) {
            case "YEARLY", "ANNUAL" -> start.plusYears(1);
            case "HALF_YEARLY", "HALFYEARLY", "SEMIANNUAL" -> start.plusMonths(6);
            case "QUARTERLY" -> start.plusMonths(3);
            default -> start.plusMonths(1);
        };
    }

    private void sendActivationEmail(User target, SubscriptionPlan plan, LocalDate startDate, LocalDate endDate, String reference) {
        if (!StringUtils.hasText(target.getEmail())) {
            return;
        }
        try {
            String origin = properties.cors() == null ? "https://www.catalogstudio.in" : properties.cors().publicAppOrigin();
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("name", StringUtils.hasText(target.getName()) ? target.getName().trim() : "there");
            vars.put("username", StringUtils.hasText(target.getUsername()) ? target.getUsername().trim() : target.getName());
            vars.put("email", target.getEmail().trim());
            vars.put("appName", "Catalog Studio");
            vars.put("plan", plan.getName());
            vars.put("price", plan.getPrice() == null ? "" : "₹" + plan.getPrice().stripTrailingZeros().toPlainString());
            vars.put("billingCycle", plan.getBillingCycle() == null ? "MONTHLY" : plan.getBillingCycle());
            vars.put("planDetail", plan.getName());
            vars.put("startDate", startDate == null ? "" : HUMAN_DATE.format(startDate));
            vars.put("endDate", endDate == null ? "" : HUMAN_DATE.format(endDate));
            vars.put("accessUntil", endDate == null ? "" : HUMAN_DATE.format(endDate));
            vars.put("reference", reference == null ? "" : reference);
            vars.put("loginLink", origin + "/login");
            templatedEmailService.send("plan-activated", target.getEmail().trim(), vars);
        } catch (RuntimeException ex) {
            log.error("Plan activation email failed for {}", target.getEmail(), ex);
        }
    }
}
