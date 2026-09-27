package com.catalogstudio.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "billing_promo_codes")
public class BillingPromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(length = 255)
    private String description;

    @Column(name = "discount_type", nullable = false, length = 20)
    private String discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Builder.Default
    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

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
        if (code != null) {
            code = code.trim().toUpperCase();
        }
        if (discountType == null || discountType.isBlank()) {
            discountType = "PERCENT";
        } else {
            discountType = discountType.trim().toUpperCase();
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        if (code != null) {
            code = code.trim().toUpperCase();
        }
        if (discountType != null) {
            discountType = discountType.trim().toUpperCase();
        }
    }

    public boolean isUsableToday() {
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return false;
        }
        LocalDate today = LocalDate.now();
        if (validFrom != null && today.isBefore(validFrom)) {
            return false;
        }
        if (validUntil != null && today.isAfter(validUntil)) {
            return false;
        }
        return maxUses == null || usedCount < maxUses;
    }
}
