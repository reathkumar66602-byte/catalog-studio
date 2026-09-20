package com.catalogstudio.security;

import com.catalogstudio.user.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record AuthUser(
        Long id,
        UUID uuid,
        String email,
        String passwordHash,
        String role,
        boolean enabled
) implements UserDetails {

    public static AuthUser from(User user) {
        return new AuthUser(
                user.getId(),
                user.getUuid(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole().name(),
                user.isActive()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
