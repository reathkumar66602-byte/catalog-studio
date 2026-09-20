package com.catalogstudio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.catalogstudio.common.api.ApiResponse;
import com.catalogstudio.config.CatalogStudioProperties;
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
public class RateLimitFilter extends OncePerRequestFilter {

    private final CatalogStudioProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(CatalogStudioProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = path.startsWith("/api/v1/auth/")
                ? properties.security().authRateLimitPerMinute()
                : properties.security().rateLimitPerMinute();
        String key = request.getRemoteAddr() + ":" + (path.startsWith("/api/v1/auth/") ? "auth" : "api");
        if (!allow(key, limit)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiResponse.error("Too many requests. Please try again shortly."));
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
