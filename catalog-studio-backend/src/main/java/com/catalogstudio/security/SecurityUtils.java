package com.catalogstudio.security;

import com.catalogstudio.common.exception.ApiException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static AuthUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw ApiException.unauthorized("Unauthorized");
        }
        return user;
    }

    public static Long currentUserId() {
        return currentUser().id();
    }

    public static Optional<AuthUser> optionalUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }
}
