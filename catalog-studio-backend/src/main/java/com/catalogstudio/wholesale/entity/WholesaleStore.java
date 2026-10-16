package com.catalogstudio.wholesale.entity;

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
@Entity
@Table(name = "wholesale_stores")
public class WholesaleStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "owner_user_id", nullable = false, unique = true, updatable = false)
    private Long ownerUserId;

    @Column(name = "store_name", nullable = false, length = 160)
    private String storeName;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(nullable = false, length = 15)
    private String gstin;

    @Column(name = "gst_legal_name")
    private String gstLegalName;

    @Column(name = "gst_verified_at")
    private Instant gstVerifiedAt;

    @Builder.Default
    @Column(name = "gst_verification_status", nullable = false, length = 32)
    private String gstVerificationStatus = "PENDING";

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 120)
    private String city;

    @Column(length = 120)
    private String state;

    @Column(length = 20)
    private String phone;

    private String email;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.DRAFT;

    @Builder.Default
    @Column(name = "product_count", nullable = false)
    private int productCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public enum Status {
        DRAFT, ACTIVE, SUSPENDED
    }
}