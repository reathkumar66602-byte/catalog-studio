package com.catalogstudio.support.config;

import com.catalogstudio.security.JsonAuthEntryPoint;
import com.catalogstudio.security.JwtProperties;
import com.catalogstudio.security.SharedJwtService;
import com.catalogstudio.security.StatelessJwtAuthFilter;
import com.catalogstudio.supportticket.service.SupportRateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SupportSecurityConfig {

    private final SupportProperties properties;
    private final ObjectMapper objectMapper;

    @Bean
    public JwtProperties jwtProperties() {
        return new JwtProperties(
                properties.jwt().secret(),
                properties.jwt().accessTokenMinutes(),
                properties.jwt().refreshTokenDays());
    }

    @Bean
    public SharedJwtService sharedJwtService(JwtProperties jwtProperties) {
        return new SharedJwtService(jwtProperties);
    }

    @Bean
    public StatelessJwtAuthFilter statelessJwtAuthFilter(SharedJwtService sharedJwtService) {
        return new StatelessJwtAuthFilter(sharedJwtService);
    }

    @Bean
    public JsonAuthEntryPoint jsonAuthEntryPoint() {
        return new JsonAuthEntryPoint(objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            StatelessJwtAuthFilter jwtAuthFilter,
            SupportRateLimitFilter rateLimitFilter,
            JsonAuthEntryPoint jsonAuthEntryPoint) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jsonAuthEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()
                        .requestMatchers("/api/v1/support/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, StatelessJwtAuthFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "chrome-extension://*"));
        if (properties.cors().allowedOrigins() != null) {
            for (String origin : properties.cors().allowedOrigins()) {
                if (origin != null && !origin.isBlank()) {
                    config.addAllowedOriginPattern(origin.trim());
                }
            }
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
