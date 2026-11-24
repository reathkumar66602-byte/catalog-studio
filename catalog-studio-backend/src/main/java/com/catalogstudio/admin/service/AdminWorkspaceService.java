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
import com.catalogstudio.subscription.dto.PriceBreakdown;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionStatus;
import com.catalogstudio.subscription.entity.PaymentTransaction.TransactionType;
import com.catalogstudio.subscription.entity.Subscription;
import com.catalogstudio.subscription.entity.Subscription.SubscriptionStatus;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.repository.SubscriptionPlanRepository;
import com.catalogstudio.subscription.repository.SubscriptionRepository;
import com.catalogstudio.subscription.service.PaymentTransactionService;
import com.catalogstudio.subscription.service.PricingService;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.subscription.service.SubscriptionActivationService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import com.catalogstudio.user.repository.UserSpecifications;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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

    private static final Set<String> SORTABLE = Set.of("name", "email", "role", "status", "createdAt", "plan", "access");
    private static final List<User.Role> WORKSPACE_ROLES = List.of(User.Role.USER, User.Role.SELLER, User.Role.TEAM_MEMBER);
    private static final List<User.Role> ADMIN_ROLES = List.of(User.Role.ADMIN, User.Role.SUPERADMIN);
    private static final String OWNER_EMAIL = "vishalmishra66602@gmail.com";
    private static final DateTimeFormatter ACCESS_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionAccessService subscriptionAccessService;
    private final SubscriptionActivationService subscriptionActivationService;
    private final PaymentTransactionService paymentTransactionService;
    private final PricingService pricingService;
    private final FeatureAccessService featureAccessService;
    private final AuditService auditService;
    private final TemplatedEmailService templatedEmailService;
    private final CatalogStudioProperties properties;

    @Transactional(readOnly = true)
    public Page<AdminUserRow> listWorkspaceUsers(String query, String accessFilter, int page, int size, String sort) {
        return listUsers(query, WORKSPACE_ROLES, accessFilter, page, size, sort);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserRow> listWorkspaceUsers(String query, int page, int size, String sort) {
        return listWorkspaceUsers(query, "ALL", page, size, sort);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserRow> listStaff(String tab, String query, int page, int size, String sort) {
        String normalized = tab == null ? "ADMIN" : tab.trim().toUpperCase(Locale.ROOT);
        List<User.Role> roles = "USER".equals(normalized) ? WORKSPACE_ROLES : ADMIN_ROLES;
        return listUsers(query, roles, "ALL", page, size, sort);
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

    private Page<AdminUserRow> listUsers(String query, List<User.Role> roles, String accessFilter, int page, int size, String sort) {
        String filter = accessFilter == null ? "ALL" : accessFilter.trim().toUpperCase(Locale.ROOT);
        boolean needsAccessFilter = switch (filter) {
            case "PAID", "TRIAL", "EXPIRED" -> true;
            default -> false;
        };
        SortRequest sortRequest = parseSort(sort);
        if (!needsAccessFilter && !sortRequest.virtual()) {
            Page<User> users = userRepository.findAll(
                    UserSpecifications.listing(query, roles, filter),
                    PageRequest.of(
                            Math.max(page, 0),
                            Math.min(Math.max(size, 1), 50),
                            Sort.by(sortRequest.direction(), sortRequest.field())));
            return mapUsers(users);
        }
        // Access-state filters and plan/access sort need subscription snapshot; page in memory.
        List<User> all = userRepository.findAll(
                UserSpecifications.listing(query, roles, filter),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Long> ids = all.stream().map(User::getId).toList();
        Map<Long, Subscription> subscriptions = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Subscription subscription : subscriptionRepository.findLatestForUsers(ids)) {
                subscriptions.put(subscription.getUser().getId(), subscription);
            }
        }
        List<User> matched = all.stream()
                .filter(user -> matchesAccessFilter(filter, user, subscriptions.get(user.getId())))
                .sorted(userComparator(sortRequest, subscriptions))
                .toList();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int from = Math.min(safePage * safeSize, matched.size());
        int to = Math.min(from + safeSize, matched.size());
        List<User> slice = matched.subList(from, to);
        Map<Long, Map<String, Boolean>> features = featureAccessService.mapForUsers(slice);
        List<AdminUserRow> rows = slice.stream()
                .map(user -> toRow(user, subscriptions.get(user.getId()),
                        features.getOrDefault(user.getId(), FeatureCatalog.defaultsEnabled())))
                .toList();
        return new org.springframework.data.domain.PageImpl<>(rows, PageRequest.of(safePage, safeSize), matched.size());
    }

    private Page<AdminUserRow> mapUsers(Page<User> users) {
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

    private boolean matchesAccessFilter(String filter, User user, Subscription subscription) {
        if (user.getStatus() == User.UserStatus.DISABLED) {
            return false;
        }
        var access = subscriptionAccessService.statusOf(user);
        return switch (filter) {
            case "PAID" -> access.accessEntitled() && !access.trialActive();
            case "TRIAL" -> access.trialActive();
            case "EXPIRED" -> !access.accessEntitled();
            default -> true;
        };
    }

    private Page<AdminUserRow> listUsers(String query, List<User.Role> roles, int page, int size, String sort) {
        return listUsers(query, roles, "ALL", page, size, sort);
    }

    private AdminUserRow activate(User actor, User target, AdminSubscriptionActionRequest request) {
        SubscriptionPlan plan = requirePlan(request.planName());
        ensureSubscription(target, plan);
        Subscription subscription = subscriptionActivationService.activatePaid(
                target,
                plan,
                "MANUAL",
                StringUtils.hasText(request.reference()) ? request.reference().trim() : "admin:" + actor.getEmail(),
                StringUtils.hasText(request.notes())
                        ? request.notes().trim()
                        : "Activated by admin after payment confirmation",
                null,
                actor
        );
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
        subscriptionAccessService.clearPendingAfterActivation(subscription);
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
        // Leaving trial for a paid plan (e.g. tutorials) so entitlements apply immediately.
        boolean paid = plan.getPrice() != null && plan.getPrice().compareTo(BigDecimal.ZERO) > 0;
        if (paid && subscription.getStatus() == SubscriptionStatus.TRIAL) {
            LocalDate start = LocalDate.now();
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setStartDate(start);
            subscription.setEndDate(endDateFor(plan, start));
        } else if (subscription.getStatus() != SubscriptionStatus.TRIAL) {
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

    private void sendActivationEmail(User target, SubscriptionPlan plan, LocalDate startDate, LocalDate endDate, String reference) {
        if (!StringUtils.hasText(target.getEmail())) {
            return;
        }
        try {
            String origin = properties.cors() == null ? "https://catalogstudio.in" : properties.cors().publicAppOrigin();
            String start = formatDate(startDate);
            String end = formatDate(endDate);
            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("name", StringUtils.hasText(target.getName()) ? target.getName().trim() : "there");
            vars.put("username", StringUtils.hasText(target.getUsername()) ? target.getUsername().trim() : target.getName());
            vars.put("email", target.getEmail().trim());
            vars.put("appName", "Catalog Studio");
            vars.put("plan", planLabel(plan));
            vars.put("price", formatPrice(plan.getPrice()));
            vars.put("billingCycle", cycleLabel(plan.getBillingCycle()));
            vars.put("planDetail", planDetail(plan));
            vars.put("startDate", start);
            vars.put("endDate", end);
            vars.put("accessUntil", end);
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

    private static String formatDate(LocalDate date) {
        return date == null ? "" : ACCESS_DATE.format(date);
    }

    private static String cycleLabel(String billingCycle) {
        String cycle = billingCycle == null ? "MONTHLY" : billingCycle.toUpperCase(Locale.ROOT);
        return switch (cycle) {
            case "YEARLY", "ANNUAL" -> "year";
            case "HALF_YEARLY", "HALFYEARLY", "SEMIANNUAL" -> "6 months";
            case "QUARTERLY" -> "quarter";
            default -> "month";
        };
    }

    private static String planLabel(SubscriptionPlan plan) {
        Map<String, Object> features = plan.getFeaturesJson() == null ? Map.of() : plan.getFeaturesJson();
        Object display = features.get("displayName");
        if (display != null && StringUtils.hasText(display.toString())) {
            return display.toString().trim();
        }
        return plan.getName();
    }

    private static String planDetail(SubscriptionPlan plan) {
        StringBuilder detail = new StringBuilder();
        detail.append(planLabel(plan)).append(" · ").append(formatPrice(plan.getPrice()));
        String cycle = cycleLabel(plan.getBillingCycle());
        if (StringUtils.hasText(cycle)) {
            detail.append(" per ").append(cycle);
        }
        Map<String, Object> features = plan.getFeaturesJson() == null ? Map.of() : plan.getFeaturesJson();
        Object unlimited = features.get("unlimitedAccess");
        boolean allAccess = Boolean.TRUE.equals(unlimited)
                || (unlimited instanceof String text && Boolean.parseBoolean(text));
        if (allAccess) {
            detail.append(". Unlimited autofill, shoot photos, and trending products");
        } else {
            List<String> includes = new java.util.ArrayList<>();
            addCount(includes, features, "monthlyAiAnalyses", "autofill uses");
            addCount(includes, features, "monthlyShootPhotos", "shoot photos");
            addCount(includes, features, "monthlyTrendingProducts", "trending products");
            if (!includes.isEmpty()) {
                detail.append(". Includes ").append(String.join(", ", includes));
            }
        }
        Object store = features.get("wholesaleSupplier");
        if (Boolean.TRUE.equals(store) || (store instanceof String text && Boolean.parseBoolean(text))) {
            detail.append(". GST wholesale store included");
        }
        Object crop = features.get("labelCrop");
        if (crop != null && StringUtils.hasText(crop.toString())) {
            detail.append(". ").append(crop.toString().trim());
        }
        return detail.toString();
    }

    private static void addCount(List<String> includes, Map<String, Object> features, String key, String label) {
        Object value = features.get(key);
        if (!(value instanceof Number number) || number.longValue() <= 0) {
            return;
        }
        includes.add(number.longValue() + " " + label);
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
        String pendingPlan = subscription == null || subscription.getPendingPlan() == null
                ? null
                : subscription.getPendingPlan().getName();
        String pendingPromo = subscription == null ? null : subscription.getPendingPromoCode();
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
                features,
                pendingPlan,
                pendingPromo,
                StringUtils.hasText(pendingPromo),
                subscription == null ? null : subscription.getPendingBaseAmount(),
                subscription == null ? null : subscription.getPendingDiscountAmount(),
                subscription == null ? null : subscription.getPendingServiceCharge(),
                subscription == null ? null : subscription.getPendingGstAmount(),
                subscription == null ? null : subscription.getPendingTotalAmount()
        );
    }

    private Pageable pageable(int page, int size, String sort) {
        SortRequest sortRequest = parseSort(sort);
        String field = sortRequest.virtual() ? "createdAt" : sortRequest.field();
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 50),
                Sort.by(sortRequest.direction(), field));
    }

    private SortRequest parseSort(String sort) {
        String raw = StringUtils.hasText(sort) ? sort.trim() : "createdAt,desc";
        String[] parts = raw.split(",", 2);
        String field = SORTABLE.contains(parts[0]) ? parts[0] : "createdAt";
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        boolean virtual = "plan".equals(field) || "access".equals(field);
        return new SortRequest(field, direction, virtual);
    }

    private Comparator<User> userComparator(SortRequest sortRequest, Map<Long, Subscription> subscriptions) {
        Comparator<User> comparator = switch (sortRequest.field()) {
            case "name" -> Comparator.comparing(u -> nullToEmpty(u.getName()), String.CASE_INSENSITIVE_ORDER);
            case "email" -> Comparator.comparing(u -> nullToEmpty(u.getEmail()), String.CASE_INSENSITIVE_ORDER);
            case "status" -> Comparator.comparing(u -> u.getStatus() == null ? "" : u.getStatus().name());
            case "role" -> Comparator.comparing(u -> u.getRole() == null ? "" : u.getRole().name());
            case "plan" -> Comparator.comparing(u -> {
                Subscription sub = subscriptions.get(u.getId());
                return sub == null || sub.getPlan() == null ? "" : nullToEmpty(sub.getPlan().getName());
            }, String.CASE_INSENSITIVE_ORDER);
            case "access" -> Comparator.comparing(u -> {
                Subscription sub = subscriptions.get(u.getId());
                return sub == null || sub.getStatus() == null ? "" : sub.getStatus().name();
            });
            default -> Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return sortRequest.direction() == Sort.Direction.DESC ? comparator.reversed() : comparator;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record SortRequest(String field, Sort.Direction direction, boolean virtual) {}

    private LocalDate endDateFor(SubscriptionPlan plan, LocalDate start) {
        return subscriptionActivationService.endDateFor(plan, start);
    }

    private boolean isOwnerEmail(String email) {
        return email != null && OWNER_EMAIL.equalsIgnoreCase(email.trim());
    }
}
