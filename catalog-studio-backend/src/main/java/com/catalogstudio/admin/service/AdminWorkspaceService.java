package com.catalogstudio.admin.service;

import com.catalogstudio.access.FeatureCatalog;
import com.catalogstudio.access.service.FeatureAccessService;
import com.catalogstudio.admin.dto.AdminSubscriptionActionRequest;
import com.catalogstudio.admin.dto.AdminUserRow;
import com.catalogstudio.admin.dto.StaffRoleActionRequest;
import com.catalogstudio.audit.service.AuditService;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.config.CatalogStudioProperties;
import com.catalogstudio.email.service.TemplatedEmailService;
import com.catalogstudio.security.Roles;
import com.catalogstudio.security.SecurityUtils;
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
import com.catalogstudio.user.repository.UserSpecifications;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminWorkspaceService {

    private static final Set<String> SORTABLE = Set.of("name", "email", "role", "status", "createdAt");
    private static final List<User.Role> WORKSPACE_ROLES = List.of(User.Role.USER, User.Role.SELLER, User.Role.TEAM_MEMBER);
    private static final List<User.Role> ADMIN_ROLES = List.of(User.Role.ADMIN, User.Role.SUPERADMIN);
    private static final String OWNER_EMAIL = "vishalmishra66602@gmail.com";
    private static final DateTimeFormatter ACCESS_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionAccessService subscriptionAccessService;
    private final PaymentTransactionService paymentTransactionService;
    private final FeatureAccessService featureAccessService;
    private final AuditService auditService;
    private final TemplatedEmailService templatedEmailService;
    private final CatalogStudioProperties properties;

    @Transactional(readOnly = true)
    public Page<AdminUserRow> listWorkspaceUsers(String query, int page, int size, String sort) {
        return listUsers(query, WORKSPACE_ROLES, page, size, sort);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserRow> listStaff(String tab, String query, int page, int size, String sort) {
        String normalized = tab == null ? "ADMIN" : tab.trim().toUpperCase(Locale.ROOT);
        List<User.Role> roles = "USER".equals(normalized) ? WORKSPACE_ROLES : ADMIN_ROLES;
        return listUsers(query, roles, page, size, sort);
    }

    @Transactional
    public AdminUserRow applySubscription(UUID userId, AdminSubscriptionActionRequest request) {
        User actor = actor();
        User target = requireWorkspaceUser(userId);
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        return switch (action) {
            case "ACTIVATE" -> activate(actor, target, request);
            case "DEACTIVATE" -> deactivate(actor, target, request);
            case "CHANGE_PLAN" -> changePlan(actor, target, request);
            default -> throw ApiException.badRequest("Use ACTIVATE, DEACTIVATE, or CHANGE_PLAN");
        };
    }

    @Transactional
    public AdminUserRow updateAccess(UUID userId, Map<String, Boolean> features) {
        User actor = actor();
        User target = requireWorkspaceUser(userId);
        Map<String, Boolean> saved = featureAccessService.replace(target, features);
        auditService.log(actor, "FEATURE_ACCESS_UPDATED", "USER", target.getUuid().toString(), null,
                Map.of("features", saved));
        return toRow(target, latest(target.getId()), saved);
    }

    @Transactional
    public AdminUserRow applyStaffRole(UUID userId, StaffRoleActionRequest request) {
        User actor = actor();
        if (!actor.isSuperAdmin()) {
            throw ApiException.forbidden("Only a super admin can change admin roles");
        }
        User target = userRepository.findByUuid(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (target.getId().equals(actor.getId())) {
            throw ApiException.badRequest("You cannot change your own role");
        }
        if (target.isSuperAdmin() || isOwnerEmail(target.getEmail())) {
            throw ApiException.forbidden("The super admin account cannot be changed from this screen");
        }
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if ("ACTIVATE".equals(action) || "PROMOTE".equals(action)) {
            if (!target.isWorkspaceUser()) {
                throw ApiException.badRequest("Only a normal user can be activated as admin");
            }
            target.setRole(User.Role.ADMIN);
            target.setStatus(User.UserStatus.ACTIVE);
            auditService.log(actor, "USER_PROMOTED_ADMIN", "USER", target.getUuid().toString(), null, Map.of());
        } else if ("DEACTIVATE".equals(action) || "DEMOTE".equals(action)) {
            if (target.getRole() != User.Role.ADMIN) {
                throw ApiException.badRequest("Only an admin can be deactivated back to a user");
            }
            target.setRole(User.Role.SELLER);
            auditService.log(actor, "ADMIN_DEMOTED_USER", "USER", target.getUuid().toString(), null, Map.of());
        } else {
            throw ApiException.badRequest("Use ACTIVATE or DEACTIVATE");
        }
        return toRow(target, latest(target.getId()), featureAccessService.mapFor(target));
    }

    public List<FeatureCatalog.FeatureDefinition> features() {
        return FeatureCatalog.ALL;
    }

    private Page<AdminUserRow> listUsers(String query, List<User.Role> roles, int page, int size, String sort) {
        Page<User> users = userRepository.findAll(
                UserSpecifications.listing(query, roles),
                pageable(page, size, sort));
        List<Long> ids = users.getContent().stream().map(User::getId).toList();
        Map<Long, Subscription> subscriptions = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Subscription subscription : subscriptionRepository.findLatestForUsers(ids)) {
                subscriptions.put(subscription.getUser().getId(), subscription);
            }
        }
        Map<Long, Map<String, Boolean>> features = featureAccessService.mapForUsers(users.getContent());
        return users.map(user -> toRow(
                user,
                subscriptions.get(user.getId()),
                features.getOrDefault(user.getId(), FeatureCatalog.defaultsEnabled())));
    }

    private AdminUserRow activate(User actor, User target, AdminSubscriptionActionRequest request) {
        SubscriptionPlan plan = requirePlan(request.planName());
        Subscription subscription = ensureSubscription(target, plan);
        LocalDate start = LocalDate.now();
        subscription.setPlan(plan);
        subscription.setPendingPlan(null);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(start);
        subscription.setEndDate(endDateFor(plan, start));
        String notes = StringUtils.hasText(request.notes())
                ? request.notes().trim()
                : "Activated after WhatsApp payment confirmation";
        String reference = StringUtils.hasText(request.reference())
                ? request.reference().trim()
                : "whatsapp:" + target.getEmail();
        paymentTransactionService.record(
                target, plan, TransactionType.ACTIVATION, TransactionStatus.SUCCESS,
                "MANUAL", reference, notes);
        auditService.log(actor, "SUBSCRIPTION_ACTIVATED", "USER", target.getUuid().toString(), null,
                Map.of("plan", plan.getName(), "reference", reference));
        sendActivationEmail(target, plan, subscription.getEndDate(), reference);
        return toRow(target, subscription, featureAccessService.mapFor(target));
    }

    private AdminUserRow deactivate(User actor, User target, AdminSubscriptionActionRequest request) {
        SubscriptionPlan plan = StringUtils.hasText(request.planName())
                ? requirePlan(request.planName())
                : null;
        Subscription subscription = latest(target.getId());
        if (subscription == null) {
            throw ApiException.badRequest("This user has no subscription to deactivate");
        }
        if (plan != null) {
            subscription.setPlan(plan);
        }
        subscription.setPendingPlan(null);
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setEndDate(LocalDate.now().minusDays(1));
        String notes = StringUtils.hasText(request.notes())
                ? request.notes().trim()
                : "Subscription deactivated by admin";
        paymentTransactionService.record(
                target,
                subscription.getPlan(),
                TransactionType.DEACTIVATION,
                TransactionStatus.EXPIRED,
                "MANUAL",
                StringUtils.hasText(request.reference()) ? request.reference().trim() : "admin:" + actor.getEmail(),
                notes);
        auditService.log(actor, "SUBSCRIPTION_DEACTIVATED", "USER", target.getUuid().toString(), null,
                Map.of("plan", subscription.getPlan().getName()));
        return toRow(target, subscription, featureAccessService.mapFor(target));
    }

    private AdminUserRow changePlan(User actor, User target, AdminSubscriptionActionRequest request) {
        SubscriptionPlan plan = requirePlan(request.planName());
        var access = subscriptionAccessService.statusOf(target);
        if (!access.accessEntitled()) {
            throw ApiException.badRequest(
                    "This seller's access has ended. After you confirm the WhatsApp payment, use Activate to open the selected plan.");
        }
        Subscription subscription = ensureSubscription(target, plan);
        String currentPlan = subscription.getPlan() == null ? "" : subscription.getPlan().getName();
        if (plan.getName().equalsIgnoreCase(currentPlan)) {
            throw ApiException.badRequest("This seller is already on the " + plan.getName() + " plan.");
        }
        subscription.setPlan(plan);
        subscription.setPendingPlan(null);
        if (subscription.getStatus() != SubscriptionStatus.TRIAL) {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
        }
        String notes = StringUtils.hasText(request.notes())
                ? request.notes().trim()
                : "Plan switched by admin from " + currentPlan + " to " + plan.getName();
        paymentTransactionService.record(
                target, plan, TransactionType.PLAN_CHANGE, TransactionStatus.SUCCESS,
                "MANUAL",
                StringUtils.hasText(request.reference()) ? request.reference().trim() : "admin-plan:" + actor.getEmail(),
                notes);
        auditService.log(actor, "SUBSCRIPTION_PLAN_CHANGED", "USER", target.getUuid().toString(), null,
                Map.of("from", currentPlan, "to", plan.getName()));
        return toRow(target, subscription, featureAccessService.mapFor(target));
    }

    private void sendActivationEmail(User target, SubscriptionPlan plan, LocalDate accessUntil, String reference) {
        if (!StringUtils.hasText(target.getEmail())) {
            return;
        }
        try {
            String origin = properties.cors() == null ? "https://catalogstudio.in" : properties.cors().publicAppOrigin();
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("name", StringUtils.hasText(target.getName()) ? target.getName().trim() : "there");
            vars.put("username", StringUtils.hasText(target.getUsername()) ? target.getUsername().trim() : target.getName());
            vars.put("email", target.getEmail().trim());
            vars.put("appName", "Catalog Studio");
            vars.put("plan", plan.getName());
            vars.put("price", formatPrice(plan.getPrice()));
            vars.put("accessUntil", accessUntil == null ? "" : ACCESS_DATE.format(accessUntil));
            vars.put("reference", reference == null ? "" : reference);
            vars.put("loginLink", origin + "/login");
            boolean sent = templatedEmailService.send("plan-activated", target.getEmail().trim(), vars);
            if (sent) {
                log.info("Plan activation email queued for {}", target.getEmail());
            } else {
                log.warn("Plan activation email was not delivered to {}", target.getEmail());
            }
        } catch (RuntimeException ex) {
            log.error("Plan activation email failed for {}", target.getEmail(), ex);
        }
    }

    private static String formatPrice(BigDecimal price) {
        if (price == null) {
            return "";
        }
        return "₹" + price.stripTrailingZeros().toPlainString();
    }

    private Subscription ensureSubscription(User target, SubscriptionPlan plan) {
        Subscription subscription = latest(target.getId());
        if (subscription != null) {
            return subscription;
        }
        return subscriptionRepository.save(Subscription.builder()
                .user(target)
                .plan(plan)
                .status(SubscriptionStatus.EXPIRED)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().minusDays(1))
                .build());
    }

    private User requireWorkspaceUser(UUID userId) {
        User target = userRepository.findByUuid(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!target.isWorkspaceUser()) {
            throw ApiException.badRequest("This action is only available for seller users");
        }
        return target;
    }

    private User actor() {
        return userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
    }

    private Subscription latest(Long userId) {
        return subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId).orElse(null);
    }

    private SubscriptionPlan requirePlan(String planName) {
        if (!StringUtils.hasText(planName)) {
            throw ApiException.badRequest("Select a plan");
        }
        SubscriptionPlan plan = planRepository.findByNameIgnoreCase(planName.trim())
                .orElseThrow(() -> ApiException.notFound("Plan not found"));
        if (!"ACTIVE".equalsIgnoreCase(plan.getStatus())) {
            throw ApiException.badRequest("This plan is not available");
        }
        return plan;
    }

    private AdminUserRow toRow(User user, Subscription subscription, Map<String, Boolean> features) {
        var access = subscriptionAccessService.statusOf(user);
        return new AdminUserRow(
                user.getUuid(),
                user.getName(),
                user.getEmail(),
                user.getMobile(),
                user.getUsername(),
                user.getRole().name(),
                Roles.displayName(user.getRole()),
                user.getStatus().name(),
                access.plan(),
                access.effectiveStatus(),
                access.accessEntitled(),
                access.startDate(),
                access.endDate(),
                user.getCreatedAt(),
                features
        );
    }

    private Pageable pageable(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String raw = StringUtils.hasText(sort) ? sort.trim() : "createdAt,desc";
        String[] parts = raw.split(",", 2);
        String field = SORTABLE.contains(parts[0]) ? parts[0] : "createdAt";
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(direction, field));
    }

    private LocalDate endDateFor(SubscriptionPlan plan, LocalDate start) {
        String cycle = plan.getBillingCycle() == null ? "MONTHLY" : plan.getBillingCycle().toUpperCase(Locale.ROOT);
        return switch (cycle) {
            case "YEARLY", "ANNUAL" -> start.plusYears(1);
            case "QUARTERLY" -> start.plusMonths(3);
            default -> start.plusMonths(1);
        };
    }

    private boolean isOwnerEmail(String email) {
        return email != null && OWNER_EMAIL.equalsIgnoreCase(email.trim());
    }
}
