package com.catalogstudio.user.controller;

import com.catalogstudio.access.service.FeatureAccessService;
import com.catalogstudio.analysis.repository.ProductAnalysisRepository;
import com.catalogstudio.auth.entity.UserSession;
import com.catalogstudio.auth.repository.UserSessionRepository;
import com.catalogstudio.business.entity.Business;
import com.catalogstudio.business.repository.BusinessRepository;
import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.extension.repository.ExtensionDeviceRepository;
import com.catalogstudio.marketplace.entity.MarketplaceConnection;
import com.catalogstudio.marketplace.repository.MarketplaceConnectionRepository;
import com.catalogstudio.product.repository.AutofillProfileRepository;
import com.catalogstudio.product.repository.ListingTemplateRepository;
import com.catalogstudio.product.repository.ProductRepository;
import com.catalogstudio.security.SecurityUtils;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.user.SupportedLocales;
import com.catalogstudio.user.dto.BillingAddressRequest;
import com.catalogstudio.user.dto.BillingAddressResponse;
import com.catalogstudio.user.dto.ChangePasswordRequest;
import com.catalogstudio.user.dto.MeResponse;
import com.catalogstudio.user.dto.UpdateProfileRequest;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Account")
public class AccountController {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final SubscriptionAccessService subscriptionAccessService;
    private final ProductRepository productRepository;
    private final ProductAnalysisRepository analysisRepository;
    private final ListingTemplateRepository templateRepository;
    private final AutofillProfileRepository profileRepository;
    private final MarketplaceConnectionRepository connectionRepository;
    private final ExtensionDeviceRepository deviceRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final FeatureAccessService featureAccessService;

    @GetMapping("/me")
    @Transactional
    public ApiResponse<MeResponse> me() {
        User user = user();
        Business business = businessRepository.findByUserId(user.getId()).orElse(null);
        SubscriptionStatusResponse access = subscriptionAccessService.statusOf(user);
        return ApiResponse.ok(new MeResponse(
                user.getUuid(), user.getName(), user.getUsername(), user.getEmail(), user.getMobile(), user.getRole().name(),
                user.isEmailVerified(), user.isMobileVerified(), user.getPreferredAiProvider(),
                SupportedLocales.normalize(user.getPreferredLocale()),
                business == null ? null : business.getBusinessName(),
                business == null ? null : business.getGstNumber(),
                business == null ? null : business.getAddress(),
                access.plan(),
                "system",
                access.effectiveStatus(),
                access.accessEntitled(),
                access.requiresRecharge(),
                access.endDate(),
                access.daysRemaining(),
                featureAccessService.enabledKeys(user)
        ));
    }

    @PutMapping("/me")
    @Transactional
    public ApiResponse<MeResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        User user = user();
        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.mobile() != null) {
            user.setMobile(request.mobile());
        }
        if (request.preferredAiProvider() != null) {
            user.setPreferredAiProvider(request.preferredAiProvider());
        }
        if (request.preferredLocale() != null && !request.preferredLocale().isBlank()) {
            user.setPreferredLocale(SupportedLocales.normalize(request.preferredLocale()));
        }
        Business business = businessRepository.findByUserId(user.getId()).orElse(null);
        if (business != null) {
            if (request.businessName() != null) {
                business.setBusinessName(request.businessName());
            }
            if (request.gstNumber() != null) {
                business.setGstNumber(request.gstNumber());
            }
            if (request.address() != null) {
                business.setAddress(request.address());
            }
        }
        return me();
    }

    @GetMapping("/me/billing-address")
    @Transactional(readOnly = true)
    public ApiResponse<BillingAddressResponse> billingAddress() {
        return ApiResponse.ok(toBilling(businessOrCreate(user())));
    }

    @PutMapping("/me/billing-address")
    @Transactional
    public ApiResponse<BillingAddressResponse> updateBilling(@Valid @RequestBody BillingAddressRequest request) {
        User user = user();
        Business business = businessOrCreate(user);
        if (request.addressLine1() != null) {
            business.setAddressLine1(request.addressLine1());
        }
        if (request.addressLine2() != null) {
            business.setAddressLine2(request.addressLine2());
        }
        if (request.city() != null) {
            business.setCity(request.city());
        }
        if (request.state() != null) {
            business.setState(request.state());
        }
        if (request.postalCode() != null) {
            business.setPostalCode(request.postalCode());
        }
        if (request.country() != null) {
            business.setCountry(request.country());
        }
        if (request.landmark() != null) {
            business.setLandmark(request.landmark());
        }
        if (request.googlePlaceId() != null) {
            business.setGooglePlaceId(request.googlePlaceId());
        }
        if (request.latitude() != null) {
            business.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            business.setLongitude(request.longitude());
        }
        if (request.gstNumber() != null) {
            business.setGstNumber(request.gstNumber());
        }
        business.setAddress(composeAddress(business));
        return ApiResponse.ok("Billing address saved", toBilling(business));
    }

    @PutMapping("/me/password")
    @Transactional
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        User user = user();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        return ApiResponse.okMessage("Password updated");
    }

    @GetMapping("/dashboard")
    @Transactional
    public ApiResponse<Map<String, Object>> dashboard() {
        Long userId = SecurityUtils.currentUserId();
        Instant monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        SubscriptionStatusResponse access = subscriptionAccessService.statusOf(userId);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("totalProducts", productRepository.countByUserId(userId));
        body.put("aiAnalysesThisMonth", analysisRepository.countByUserIdAndCreatedAtAfter(userId, monthStart));
        body.put("savedTemplates", templateRepository.countByUserId(userId));
        body.put("autofillProfiles", profileRepository.countByUserId(userId));
        body.put("connectedMarketplaces", connectionRepository.countByUserIdAndStatus(
                userId, MarketplaceConnection.ConnectionStatus.CONNECTED));
        body.put("currentPlan", access.plan());
        body.put("planStatus", access.effectiveStatus());
        body.put("accessEntitled", access.accessEntitled());
        body.put("requiresRecharge", access.requiresRecharge());
        body.put("daysRemaining", access.daysRemaining());
        body.put("trialEndsOn", access.endDate() == null ? "" : access.endDate());
        body.put("extensionDevices", deviceRepository.countByUserIdAndStatus(
                userId, com.catalogstudio.extension.entity.ExtensionDevice.DeviceStatus.ACTIVE));
        return ApiResponse.ok(body);
    }

    @GetMapping("/me/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions() {
        List<UserSession> sessions = sessionRepository.findByUserIdAndRevokedFalse(SecurityUtils.currentUserId());
        return ApiResponse.ok(sessions.stream().map(s -> Map.<String, Object>of(
                "id", s.getUuid(),
                "deviceName", s.getDeviceName() == null ? "Unknown device" : s.getDeviceName(),
                "ipAddress", s.getIpAddress() == null ? "" : s.getIpAddress(),
                "trustedDevice", s.isTrustedDevice(),
                "lastUsedAt", s.getLastUsedAt() == null ? "" : s.getLastUsedAt().toString(),
                "createdAt", s.getCreatedAt().toString()
        )).toList());
    }

    private User user() {
        return userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> ApiException.unauthorized("Unauthorized"));
    }

    private Business businessOrCreate(User user) {
        return businessRepository.findByUserId(user.getId()).orElseGet(() -> businessRepository.save(Business.builder()
                .user(user)
                .businessName(user.getName())
                .country("India")
                .build()));
    }

    private BillingAddressResponse toBilling(Business business) {
        return new BillingAddressResponse(
                business.getUuid(),
                business.getBusinessName(),
                business.getGstNumber(),
                business.getAddress(),
                business.getAddressLine1(),
                business.getAddressLine2(),
                business.getCity(),
                business.getState(),
                business.getPostalCode(),
                business.getCountry(),
                business.getLandmark(),
                business.getGooglePlaceId(),
                business.getLatitude(),
                business.getLongitude()
        );
    }

    private String composeAddress(Business business) {
        return String.join(", ", java.util.stream.Stream.of(
                        business.getAddressLine1(),
                        business.getAddressLine2(),
                        business.getLandmark(),
                        business.getCity(),
                        business.getState(),
                        business.getPostalCode(),
                        business.getCountry())
                .filter(part -> part != null && !part.isBlank())
                .toList());
    }
}
