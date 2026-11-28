package com.catalogstudio.supportticket.service;

import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.support.config.SupportProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SupportRateLimitFilter extends OncePerRequestFilter {

    private final SupportProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public SupportRateLimitFilter(SupportProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI();
        int limit;
        String bucket;
        if (path.matches(".*/api/v1/support/chat/sessions/[^/]+/messages/?$")) {
            limit = properties.support().chatRateLimitPerMinute();
            bucket = "chat";
        } else if (path.endsWith("/api/v1/support/tickets") || path.endsWith("/api/v1/support/tickets/")) {
            limit = properties.support().ticketCreateRateLimitPerMinute();
            bucket = "ticket";
        } else {
            filterChain.doFilter(request, response);
            return;
        }
        String key = request.getRemoteAddr() + ":" + bucket;
        if (!allow(key, limit)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiResponse.error("Too many requests. Please try again shortly."));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean allow(String key, int limit) {
        long now = Instant.now().getEpochSecond();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startEpoch >= 60) {
                return new Window(now, 1);
            }
            existing.count++;
            return existing;
        });
        return window.count <= limit;
    }

    private static final class Window {
        private final long startEpoch;
        private int count;

        private Window(long startEpoch, int count) {
            this.startEpoch = startEpoch;
            this.count = count;
        }
    }
}
