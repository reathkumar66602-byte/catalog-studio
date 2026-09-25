package com.catalogstudio.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.catalogstudio.access.service.FeatureAccessService;
import com.catalogstudio.admin.dto.AdminSubscriptionActionRequest;
import com.catalogstudio.admin.dto.StaffRoleActionRequest;
import com.catalogstudio.admin.service.AdminWorkspaceService;
import com.catalogstudio.audit.service.AuditService;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.email.service.TemplatedEmailService;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionStatus;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionType;
import com.catalogstudio.subscription.entity.Subscription;
import com.catalogstudio.subscription.entity.Subscription.SubscriptionStatus;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.repository.SubscriptionPlanRepository;
import com.catalogstudio.subscription.repository.SubscriptionRepository;
import com.catalogstudio.subscription.service.PaymentTransactionService;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminWorkspaceServiceTest {

    @Mock UserRepository userRepository;
    @Mock SubscriptionRepository subscriptionRepository;
    @Mock SubscriptionPlanRepository planRepository;
    @Mock SubscriptionAccessService subscriptionAccessService;
    @Mock PaymentTransactionService paymentTransactionService;
    @Mock FeatureAccessService featureAccessService;
    @Mock AuditService auditService;
    @Mock TemplatedEmailService templatedEmailService;
    @Mock CatalogStudioProperties properties;
    @InjectMocks AdminWorkspaceService workspaceService;

    private MockedStatic<SecurityUtils> security;
    private User superAdmin;
    private User seller;
    private SubscriptionPlan basic;

    @BeforeEach
    void setUp() {
        superAdmin = User.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .name("Vishal")
                .email("vishalmishra66602@gmail.com")
                .role(User.Role.SUPERADMIN)
                .status(User.UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
        seller = User.builder()
                .id(11L)
                .uuid(UUID.randomUUID())
                .name("Seller A")
                .email("seller@example.com")
                .role(User.Role.SELLER)
                .status(User.UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
        basic = SubscriptionPlan.builder()
                .id(3L)
                .name("BASIC")
                .price(new BigDecimal("49"))
                .billingCycle("MONTHLY")
                .status("ACTIVE")
                .build();
        security = org.mockito.Mockito.mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::currentUserId).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(superAdmin));
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    @Test
    void activateWritesWhatsAppTransactionAndOpensAccess() {
        when(userRepository.findByUuid(seller.getUuid())).thenReturn(Optional.of(seller));
        when(planRepository.findByNameIgnoreCase("BASIC")).thenReturn(Optional.of(basic));
        Subscription expired = Subscription.builder()
                .user(seller)
                .plan(basic)
                .status(SubscriptionStatus.EXPIRED)
                .startDate(LocalDate.now().minusDays(10))
                .endDate(LocalDate.now().minusDays(8))
                .build();
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(11L)).thenReturn(Optional.of(expired));
        when(featureAccessService.mapFor(seller)).thenReturn(Map.of("meesho_calculator", true));
        when(subscriptionAccessService.statusOf(seller)).thenReturn(status("BASIC", true));
        when(properties.cors()).thenReturn(new CatalogStudioProperties.Cors(List.of("https://catalogstudio.in")));
        when(templatedEmailService.send(eq("plan-activated"), eq("seller@example.com"), org.mockito.ArgumentMatchers.<Map<String, String>>any()))
                .thenReturn(true);

        workspaceService.applySubscription(seller.getUuid(), new AdminSubscriptionActionRequest(
                "ACTIVATE", "BASIC", "UTR123", null));

        assertThat(expired.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(expired.getEndDate()).isEqualTo(LocalDate.now().plusMonths(1));
        verify(paymentTransactionService).record(
                eq(seller), eq(basic), eq(TransactionType.ACTIVATION), eq(TransactionStatus.SUCCESS),
                eq("MANUAL"), eq("UTR123"), eq("Activated after WhatsApp payment confirmation"));
        verify(templatedEmailService).send(
                eq("plan-activated"), eq("seller@example.com"), org.mockito.ArgumentMatchers.<Map<String, String>>any());
    }

    @Test
    void activateOnUserTabPromotesSellerToAdmin() {
        when(userRepository.findByUuid(seller.getUuid())).thenReturn(Optional.of(seller));
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(11L)).thenReturn(Optional.empty());
        when(featureAccessService.mapFor(seller)).thenReturn(Map.of());
        when(subscriptionAccessService.statusOf(seller)).thenReturn(status("NONE", false));

        workspaceService.applyStaffRole(seller.getUuid(), new StaffRoleActionRequest("ACTIVATE"));

        assertThat(seller.getRole()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    void changePlanRejectsExpiredAccess() {
        when(userRepository.findByUuid(seller.getUuid())).thenReturn(Optional.of(seller));
        when(planRepository.findByNameIgnoreCase("PRO")).thenReturn(Optional.of(pro()));
        when(subscriptionAccessService.statusOf(seller)).thenReturn(status("BASIC", false));

        assertThatThrownBy(() -> workspaceService.applySubscription(
                seller.getUuid(), new AdminSubscriptionActionRequest("CHANGE_PLAN", "PRO", null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("use Activate");
    }

    @Test
    void changePlanRejectsSamePlan() {
        when(userRepository.findByUuid(seller.getUuid())).thenReturn(Optional.of(seller));
        when(planRepository.findByNameIgnoreCase("BASIC")).thenReturn(Optional.of(basic));
        when(subscriptionAccessService.statusOf(seller)).thenReturn(status("BASIC", true));
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(11L)).thenReturn(Optional.of(
                Subscription.builder()
                        .user(seller)
                        .plan(basic)
                        .status(SubscriptionStatus.ACTIVE)
                        .startDate(LocalDate.now().minusDays(2))
                        .endDate(LocalDate.now().plusDays(20))
                        .build()));

        assertThatThrownBy(() -> workspaceService.applySubscription(
                seller.getUuid(), new AdminSubscriptionActionRequest("CHANGE_PLAN", "BASIC", null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already on the BASIC plan");
    }

    @Test
    void changePlanSwitchesActivePlanWithoutChangingDates() {
        SubscriptionPlan pro = pro();
        LocalDate end = LocalDate.now().plusDays(20);
        Subscription current = Subscription.builder()
                .user(seller)
                .plan(basic)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now().minusDays(2))
                .endDate(end)
                .build();
        when(userRepository.findByUuid(seller.getUuid())).thenReturn(Optional.of(seller));
        when(planRepository.findByNameIgnoreCase("PRO")).thenReturn(Optional.of(pro));
        when(subscriptionAccessService.statusOf(seller)).thenReturn(status("BASIC", true));
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(11L)).thenReturn(Optional.of(current));
        when(featureAccessService.mapFor(seller)).thenReturn(Map.of());

        workspaceService.applySubscription(
                seller.getUuid(), new AdminSubscriptionActionRequest("CHANGE_PLAN", "PRO", "admin-ref", null));

        assertThat(current.getPlan()).isEqualTo(pro);
        assertThat(current.getEndDate()).isEqualTo(end);
        assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(paymentTransactionService).record(
                eq(seller), eq(pro), eq(TransactionType.PLAN_CHANGE), eq(TransactionStatus.SUCCESS),
                eq("MANUAL"), eq("admin-ref"), org.mockito.ArgumentMatchers.contains("BASIC"));
    }

    @Test
    void cannotDemoteAnotherSuperAdmin() {
        User other = User.builder()
                .id(3L)
                .uuid(UUID.randomUUID())
                .name("Other")
                .email("other-super@example.com")
                .role(User.Role.SUPERADMIN)
                .status(User.UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
        when(userRepository.findByUuid(other.getUuid())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> workspaceService.applyStaffRole(
                other.getUuid(), new StaffRoleActionRequest("DEACTIVATE")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("super admin");
    }

    private SubscriptionPlan pro() {
        return SubscriptionPlan.builder()
                .id(4L)
                .name("PRO")
                .price(new BigDecimal("199"))
                .billingCycle("MONTHLY")
                .status("ACTIVE")
                .build();
    }

    private SubscriptionStatusResponse status(String plan, boolean entitled) {
        return new SubscriptionStatusResponse(
                plan, entitled ? "ACTIVE" : "EXPIRED", entitled ? "ACTIVE" : "EXPIRED",
                LocalDate.now().toString(), LocalDate.now().plusMonths(1).toString(),
                entitled, !entitled, false, entitled ? 20 : 0, Map.of(),
                "seller@example.com", 2, "Recharge", "Body", null, null);
    }
}
