package com.catalogstudio.security;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.subscription.dto.SubscriptionStatusResponse;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class SubscriptionAccessFilter extends OncePerRequestFilter {

    private static final Set<String> OPEN_PREFIXES = Set.of(
            "/api/v1/auth",
            "/api/v1/me",
            "/api/v1/subscriptions",
            "/api/v1/site",
            "/api/v1/files",
            "/api/v1/referrals",
            "/api/v1/places",
            "/api/v1/admin",
            "/api/v1/extension",
            "/swagger-ui",
            "/v3/api-docs",
            "/actuator"
    );

    private final SubscriptionAccessService accessService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || !isGated(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        AuthUser user = SecurityUtils.optionalUser().orElse(null);
        if (user == null || "ADMIN".equals(user.role())) {
            filterChain.doFilter(request, response);
            return;
        }
        SubscriptionStatusResponse access = accessService.statusOf(user.id());
        if (access.accessEntitled()) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_PAYMENT_REQUIRED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(
                access.rechargeHeadline() == null
                        ? "Your trial has ended. Recharge a plan to continue."
                        : access.rechargeHeadline()));
    }

    private boolean isGated(String uri) {
        if (uri == null || !uri.startsWith("/api/")) {
            return false;
        }
        for (String prefix : OPEN_PREFIXES) {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                return false;
            }
        }
        return true;
    }
}
