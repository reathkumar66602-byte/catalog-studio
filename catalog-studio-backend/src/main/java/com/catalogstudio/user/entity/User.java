package com.catalogstudio.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "passwordHash"})
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(unique = true, length = 80)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(length = 20)
    private String mobile;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "mobile_verified", nullable = false)
    private boolean mobileVerified;

    @Column(name = "preferred_ai_provider", length = 32)
    private String preferredAiProvider;

    @Builder.Default
    @Column(name = "preferred_locale", nullable = false, length = 16)
    private String preferredLocale = "en";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (role == null) {
            role = Role.SELLER;
        }
        if (status == null) {
            status = UserStatus.ACTIVE;
        }
        if (preferredLocale == null || preferredLocale.isBlank()) {
            preferredLocale = "en";
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public enum Role {
        ADMIN, SELLER, TEAM_MEMBER
    }

    public enum UserStatus {
        ACTIVE, DISABLED, PENDING
    }
}
