package com.catalogstudio.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.entity.BillingSettings;
import com.catalogstudio.subscription.entity.Subscription;
import com.catalogstudio.subscription.entity.Subscription.SubscriptionStatus;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.payment.PaymentProvider;
import com.catalogstudio.subscription.repository.SubscriptionPlanRepository;
import com.catalogstudio.subscription.repository.SubscriptionRepository;
import com.catalogstudio.subscription.service.BillingSettingsService;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.subscription.service.UpiQrService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionAccessServiceTest {

    @Mock UserRepository userRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionPlanRepository planRepository;
    @Mock BillingSettingsService billingSettingsService;
    @Mock UpiQrService upiQrService;
    @Mock PaymentProvider paymentProvider;
    @InjectMocks SubscriptionAccessService accessService;

    private User seller;
    private SubscriptionPlan pro;
    private SubscriptionPlan basic;
    private BillingSettings billing;

    @BeforeEach
    void setUp() {
        seller = User.builder()
                .id(11L)
                .name("Seller")
                .email("seller@example.com")
                .role(User.Role.SELLER)
                .status(User.UserStatus.ACTIVE)
                .build();
        pro = SubscriptionPlan.builder()
                .id(2L)
                .name("PRO")
                .price(new BigDecimal("499"))
                .billingCycle("MONTHLY")
                .status("ACTIVE")
                .featuresJson(Map.of("monthlyAiAnalyses", 100))
                .build();
        basic = SubscriptionPlan.builder()
                .id(3L)
                .name("BASIC")
                .price(new BigDecimal("49"))
                .billingCycle("MONTHLY")
                .status("ACTIVE")
                .featuresJson(Map.of("monthlyAiAnalyses", 8))
                .build();
        billing = BillingSettings.builder()
                .trialDays(2)
                .trialPlan("BASIC")
                .whatsappNumber("919876543210")
                .upiId("catalogstudio@upi")
                .payeeName("Catalog Studio")
                .rechargeHeadline("Recharge to keep using Catalog Studio")
                .rechargeBody("Trial ended")
                .paymentInstructions("Send the screenshot on WhatsApp")
                .whatsappMessageTemplate("Paid {{plan}} {{amount}} {{email}}")
                .build();
        when(billingSettingsService.current()).thenReturn(billing);
        lenient().when(billingSettingsService.resolvedWhatsappNumber(any())).thenReturn("919560111849");
        lenient().when(billingSettingsService.scannerImageUrl(any())).thenReturn("/payment-qr.jpg");
    }

    @Test
    void firstLoginStartsConfiguredBasicTwoDayTrial() {
        when(planRepository.findByNameIgnoreCase("BASIC")).thenReturn(Optional.of(basic));
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(11L)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        accessService.ensureTrialOnLogin(seller);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());
        Subscription saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
        assertThat(saved.getPlan()).isEqualTo(basic);
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.now().plusDays(1));
        assertThat(saved.getTrialStartedAt()).isNotNull();
    }

    @Test
    void loginStartsTrialClockOnce() {
        Subscription trial = Subscription.builder()
                .user(seller)
                .plan(pro)
                .status(SubscriptionStatus.TRIAL)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().minusDays(5))
                .trialStartedAt(null)
                .build();
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(11L)).thenReturn(Optional.of(trial));

        accessService.ensureTrialOnLogin(seller);

        assertThat(trial.getTrialStartedAt()).isNotNull();
        assertThat(trial.getStartDate()).isEqualTo(LocalDate.now());
        assertThat(trial.getEndDate()).isEqualTo(LocalDate.now().plusDays(1));
        assertThat(trial.getStatus()).isEqualTo(SubscriptionStatus.TRIAL);
    }

    @Test
    void expiredTrialForcesRechargeAndLeavesPaidAccountsEntitled() {
        Subscription expired = Subscription.builder()
                .user(seller)
                .plan(pro)
                .status(SubscriptionStatus.TRIAL)
                .startDate(LocalDate.now().minusDays(3))
                .endDate(LocalDate.now().minusDays(1))
                .trialStartedAt(java.time.Instant.now())
                .build();
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(11L)).thenReturn(Optional.of(expired));

        SubscriptionStatusResponse expiredView = accessService.statusOf(seller);
        assertThat(expiredView.accessEntitled()).isFalse();
        assertThat(expiredView.requiresRecharge()).isTrue();
        assertThat(expiredView.effectiveStatus()).isEqualTo("EXPIRED");

        Subscription paid = Subscription.builder()
                .user(seller)
                .plan(pro)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(1))
                .build();
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(11L)).thenReturn(Optional.of(paid));
        SubscriptionStatusResponse paidView = accessService.statusOf(seller);
        assertThat(paidView.accessEntitled()).isTrue();
        assertThat(paidView.requiresRecharge()).isFalse();
    }

    @Test
    void adminAlwaysHasAccess() {
        User admin = User.builder()
                .id(1L)
                .email("admin@example.com")
                .role(User.Role.ADMIN)
                .status(User.UserStatus.ACTIVE)
                .build();
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());
        SubscriptionStatusResponse view = accessService.statusOf(admin);
        assertThat(view.accessEntitled()).isTrue();
        assertThat(view.requiresRecharge()).isFalse();
    }

    @Test
    void superAdminAlwaysHasAccess() {
        User superAdmin = User.builder()
                .id(2L)
                .email("vishalmishra66602@gmail.com")
                .role(User.Role.SUPERADMIN)
                .status(User.UserStatus.ACTIVE)
                .build();
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.empty());
        SubscriptionStatusResponse view = accessService.statusOf(superAdmin);
        assertThat(view.accessEntitled()).isTrue();
        assertThat(view.requiresRecharge()).isFalse();
    }
}
