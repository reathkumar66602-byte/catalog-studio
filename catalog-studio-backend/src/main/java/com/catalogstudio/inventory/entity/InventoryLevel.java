package com.catalogstudio.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
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
@Table(name = "inventory_levels")
public class InventoryLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, unique = true)
    private ProductVariant variant;

    @Builder.Default
    @Column(name = "location_code", nullable = false, length = 40)
    private String locationCode = "DEFAULT";

    @Builder.Default
    @Column(name = "on_hand", nullable = false)
    private int onHand = 0;

    @Builder.Default
    @Column(nullable = false)
    private int reserved = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public int available() {
        return Math.max(0, onHand - reserved);
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
        if (locationCode == null || locationCode.isBlank()) {
            locationCode = "DEFAULT";
        }
    }
}
