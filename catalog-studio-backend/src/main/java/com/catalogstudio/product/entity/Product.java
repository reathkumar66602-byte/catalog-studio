package com.catalogstudio.product.entity;

import com.catalogstudio.business.entity.Business;
import com.catalogstudio.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    private Business business;

    @Column(length = 255)
    private String name;

    @Column(name = "product_type", length = 120)
    private String productType;

    @Column(length = 120)
    private String category;

    @Column(length = 120)
    private String subcategory;

    @Column(length = 40)
    private String gender;

    @Column(name = "age_group", length = 40)
    private String ageGroup;

    @Column(name = "primary_color", length = 80)
    private String primaryColor;

    @Column(length = 80)
    private String pattern;

    @Column(length = 80)
    private String material;

    @Column(name = "sleeve_type", length = 80)
    private String sleeveType;

    @Column(name = "neck_type", length = 80)
    private String neckType;

    @Column(name = "collar_type", length = 80)
    private String collarType;

    @Column(length = 80)
    private String fit;

    @Column(length = 80)
    private String occasion;

    @Column(length = 80)
    private String style;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @org.hibernate.annotations.BatchSize(size = 25)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("imageOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @Builder.Default
    @org.hibernate.annotations.BatchSize(size = 25)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductAttribute> attributes = new ArrayList<>();

    @Builder.Default
    @org.hibernate.annotations.BatchSize(size = 25)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductTitle> titles = new ArrayList<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (status == null) {
            status = ProductStatus.DRAFT;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public enum ProductStatus {
        DRAFT, ACTIVE, ARCHIVED
    }
}
