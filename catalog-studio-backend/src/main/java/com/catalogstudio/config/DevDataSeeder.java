package com.catalogstudio.config;

import com.catalogstudio.business.entity.Business;
import com.catalogstudio.business.repository.BusinessRepository;
import com.catalogstudio.subscription.entity.Subscription;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.repository.SubscriptionPlanRepository;
import com.catalogstudio.subscription.repository.SubscriptionRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;
    private final CatalogStudioProperties properties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUser(properties.seed().adminEmail(), properties.seed().adminPassword(), "Catalog Studio Admin",
                User.Role.ADMIN, "Catalog Studio HQ");
        seedUser(properties.seed().demoSellerEmail(), properties.seed().demoSellerPassword(), "Demo Seller",
                User.Role.SELLER, "Demo Store");
        promoteOwner();
    }

    private void promoteOwner() {
        userRepository.findByEmailIgnoreCase("vishalmishra66602@gmail.com")
                .or(() -> userRepository.findByEmailIgnoreCase("vishalmishra66602@gmail"))
                .ifPresent(user -> {
                    if (user.getRole() != User.Role.SUPERADMIN) {
                        user.setRole(User.Role.SUPERADMIN);
                        user.setStatus(User.UserStatus.ACTIVE);
                        log.info("Promoted {} to SUPERADMIN", user.getEmail());
                    }
                    assignStaffPlan(user);
                });
    }

    private void assignStaffPlan(User user) {
        SubscriptionPlan plan = planRepository.findByNameIgnoreCase("BUSINESS")
                .or(() -> planRepository.findByNameIgnoreCase("PRO"))
                .orElse(null);
        if (plan == null) {
            return;
        }
        Subscription subscription = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .orElse(null);
        if (subscription == null) {
            subscriptionRepository.save(Subscription.builder()
                    .user(user)
                    .plan(plan)
                    .status(Subscription.SubscriptionStatus.ACTIVE)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusYears(1))
                    .build());
            return;
        }
        if (subscription.getPlan() != null && "BUSINESS".equalsIgnoreCase(subscription.getPlan().getName())
                && subscription.getStatus() == Subscription.SubscriptionStatus.ACTIVE) {
            return;
        }
        subscription.setPlan(plan);
        subscription.setPendingPlan(null);
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDate.now());
        subscription.setEndDate(LocalDate.now().plusYears(1));
        log.info("Set {} staff plan to BUSINESS", user.getEmail());
    }

    private void seedUser(String email, String password, String name, User.Role role, String businessName) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            userRepository.findByEmailIgnoreCase(email).ifPresent(existing -> {
                if (existing.getUsername() == null || existing.getUsername().isBlank()) {
                    existing.setUsername(email.split("@")[0].replaceAll("[^A-Za-z0-9._-]", ""));
                }
            });
            return;
        }
        User user = userRepository.save(User.builder()
                .name(name)
                .username(email.split("@")[0].replaceAll("[^A-Za-z0-9._-]", ""))
                .email(email.toLowerCase())
                .mobile("9999999999")
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .status(User.UserStatus.ACTIVE)
                .emailVerified(true)
                .build());
        businessRepository.save(Business.builder().user(user).businessName(businessName).build());
        SubscriptionPlan plan = planRepository.findByNameIgnoreCase(role.isStaff() ? "BUSINESS" : "PRO")
                .or(() -> planRepository.findByNameIgnoreCase("FREE"))
                .orElse(null);
        if (plan != null) {
            subscriptionRepository.save(Subscription.builder()
                    .user(user)
                    .plan(plan)
                    .status(Subscription.SubscriptionStatus.ACTIVE)
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusYears(1))
                    .build());
        }
        log.info("Seeded {} account {}", role, email);
    }
}
