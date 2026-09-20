package com.catalogstudio.site.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "clients")
public class ClientStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "store_name", nullable = false, length = 200)
    private String storeName;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "owner_name", length = 120)
    private String ownerName;

    @Column(length = 255)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(length = 255)
    private String tagline;

    @Column(columnDefinition = "text")
    private String about;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "branding_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> brandingJson = new LinkedHashMap<>();

    @Builder.Default
    @Column(nullable = false)
    private boolean featured = false;

    @Column(nullable = false, length = 32)
    private String status;

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
        if (status == null) {
            status = "ACTIVE";
        }
        if (brandingJson == null) {
            brandingJson = new LinkedHashMap<>();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
