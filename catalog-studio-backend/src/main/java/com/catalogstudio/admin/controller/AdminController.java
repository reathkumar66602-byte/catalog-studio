package com.catalogstudio.admin.controller;

import com.catalogstudio.analysis.repository.ProductAnalysisRepository;
import com.catalogstudio.audit.entity.AuditLog;
import com.catalogstudio.audit.repository.AuditLogRepository;
import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.marketplace.entity.MarketplaceAttributeMapping;
import com.catalogstudio.marketplace.repository.MarketplaceAttributeMappingRepository;
import com.catalogstudio.subscription.entity.SubscriptionPlan;
import com.catalogstudio.subscription.repository.SubscriptionPlanRepository;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminController {

    private final UserRepository userRepository;
    private final ProductAnalysisRepository analysisRepository;
    private final AuditLogRepository auditLogRepository;
    private final SubscriptionPlanRepository planRepository;
    private final MarketplaceAttributeMappingRepository mappingRepository;

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Instant monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return ApiResponse.ok(Map.of(
                "users", userRepository.count(),
                "activeUsers", userRepository.countByStatus(User.UserStatus.ACTIVE),
                "aiAnalysesThisMonth", analysisRepository.countByCreatedAtAfter(monthStart)
        ));
    }

    @GetMapping("/users")
    public ApiResponse<Page<Map<String, Object>>> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(userRepository.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(u -> Map.<String, Object>of(
                        "id", u.getUuid(),
                        "name", u.getName(),
                        "email", u.getEmail(),
                        "role", u.getRole().name(),
                        "status", u.getStatus().name(),
                        "emailVerified", u.isEmailVerified(),
                        "createdAt", u.getCreatedAt().toString()
                )));
    }

    @PostMapping("/users/{id}/disable")
    @Transactional
    public ApiResponse<Void> disable(@PathVariable UUID id) {
        User user = userRepository.findByUuid(id).orElseThrow(() -> ApiException.notFound("User not found"));
        user.setStatus(User.UserStatus.DISABLED);
        return ApiResponse.okMessage("User disabled");
    }

    @PostMapping("/users/{id}/enable")
    @Transactional
    public ApiResponse<Void> enable(@PathVariable UUID id) {
        User user = userRepository.findByUuid(id).orElseThrow(() -> ApiException.notFound("User not found"));
        user.setStatus(User.UserStatus.ACTIVE);
        return ApiResponse.okMessage("User enabled");
    }

    @GetMapping("/audit-logs")
    public ApiResponse<Page<AuditLog>> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ApiResponse.ok(auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size)));
    }

    @GetMapping("/plans")
    public ApiResponse<List<SubscriptionPlan>> plans() {
        return ApiResponse.ok(planRepository.findAll());
    }

    @GetMapping("/mappings")
    public ApiResponse<List<MarketplaceAttributeMapping>> mappings(@RequestParam(required = false) String marketplace) {
        if (marketplace == null || marketplace.isBlank()) {
            return ApiResponse.ok(mappingRepository.findAll());
        }
        return ApiResponse.ok(mappingRepository.findByMarketplace(marketplace.toUpperCase()));
    }

    @PutMapping("/mappings")
    public ApiResponse<MarketplaceAttributeMapping> upsertMapping(@RequestBody MarketplaceAttributeMapping mapping) {
        return ApiResponse.ok(mappingRepository.save(mapping));
    }
}
