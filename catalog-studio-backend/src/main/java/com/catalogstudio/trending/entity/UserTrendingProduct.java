package com.catalogstudio.trending.entity;

import com.catalogstudio.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
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
@Table(name = "user_trending_products")
public class UserTrendingProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 16)
    private String marketplace;

    @Column(name = "category_key", nullable = false, length = 64)
    private String categoryKey;

    @Column(name = "page_index", nullable = false)
    private int pageIndex;

    @Column(nullable = false)
    private int slot;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 160)
    private String brand;

    @Column(name = "price_label", length = 64)
    private String priceLabel;

    @Column(name = "mrp_label", length = 64)
    private String mrpLabel;

    @Column(length = 32)
    private String rating;

    @Column(name = "review_count", length = 32)
    private String reviewCount;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "product_url", nullable = false, columnDefinition = "text")
    private String productUrl;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    @PrePersist
    void prePersist() {
        if (seenAt == null) {
            seenAt = Instant.now();
        }
    }
}
