package com.catalogstudio.tips.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TutorialAccessServiceTest {

    @Mock UserRepository userRepository;
    @Mock SubscriptionAccessService subscriptionAccessService;

    TutorialAccessService service;
    User seller;

    @BeforeEach
    void setUp() {
        service = new TutorialAccessService(userRepository, subscriptionAccessService);
        seller = User.builder()
                .id(3L)
                .uuid(UUID.randomUUID())
                .name("Seller")
                .email("seller@test.com")
                .passwordHash("x")
                .role(User.Role.SELLER)
                .status(User.UserStatus.ACTIVE)
                .build();
    }

    @Test
    void staffAlwaysAllowed() {
        User admin = User.builder()
                .id(1L)
                .uuid(UUID.randomUUID())
                .name("Admin")
                .email("admin@test.com")
                .passwordHash("x")
                .role(User.Role.ADMIN)
                .status(User.UserStatus.ACTIVE)
                .build();
        assertTrue(service.hasTutorialAccess(admin));
    }

    @Test
    void tutorialPlanGrantedWhenEntitled() {
        when(subscriptionAccessService.statusOf(seller)).thenReturn(status("TUTORIAL_MONTHLY", true, false));
        assertTrue(service.hasTutorialAccess(seller));
    }

    @Test
    void tutorialPlanGrantedEvenIfStillMarkedTrial() {
        when(subscriptionAccessService.statusOf(seller)).thenReturn(status("TUTORIAL_MONTHLY", true, true));
        assertTrue(service.hasTutorialAccess(seller));
    }

    @Test
    void paidCatalogPlanDoesNotIncludeTutorials() {
        when(subscriptionAccessService.statusOf(seller)).thenReturn(status("PRO", true, false));
        assertFalse(service.hasTutorialAccess(seller));
    }

    @Test
    void basicTrialDenied() {
        when(subscriptionAccessService.statusOf(seller)).thenReturn(status("BASIC", true, true));
        assertFalse(service.hasTutorialAccess(seller));
    }

    @Test
    void expiredTutorialDenied() {
        when(subscriptionAccessService.statusOf(seller)).thenReturn(status("TUTORIAL_MONTHLY", false, false));
        assertFalse(service.hasTutorialAccess(seller));
    }

    private static SubscriptionStatusResponse status(String plan, boolean entitled, boolean trialActive) {
        return new SubscriptionStatusResponse(
                plan,
                entitled ? (trialActive ? "TRIAL" : "ACTIVE") : "EXPIRED",
                entitled ? (trialActive ? "TRIAL" : "ACTIVE") : "EXPIRED",
                "",
                "",
                entitled,
                !entitled,
                trialActive,
                0,
                Map.of(),
                "seller@test.com",
                0,
                null,
                null,
                null,
                null);
    }
}
