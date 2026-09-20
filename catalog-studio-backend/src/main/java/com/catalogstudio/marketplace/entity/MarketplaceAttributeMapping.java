package com.catalogstudio.marketplace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "marketplace_attribute_mappings")
public class MarketplaceAttributeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(nullable = false, length = 40)
    private String marketplace;

    @Column(name = "internal_attribute", nullable = false, length = 120)
    private String internalAttribute;

    @Column(name = "marketplace_attribute", nullable = false, length = 160)
    private String marketplaceAttribute;

    @Column(name = "internal_value", nullable = false, length = 160)
    private String internalValue;

    @Column(name = "marketplace_value", nullable = false, length = 160)
    private String marketplaceValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
