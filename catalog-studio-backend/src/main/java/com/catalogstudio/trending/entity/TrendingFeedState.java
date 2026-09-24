package com.catalogstudio.trending.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "trending_feed_state")
public class TrendingFeedState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String marketplace;

    @Column(name = "category_key", nullable = false, length = 64)
    private String categoryKey;

    @Column(name = "next_cursor", columnDefinition = "text")
    private String nextCursor;

    @Column(name = "next_page", nullable = false)
    private int nextPage;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @PrePersist
    void prePersist() {
        if (fetchedAt == null) {
            fetchedAt = Instant.now();
        }
        if (nextPage < 1) {
            nextPage = 1;
        }
    }
}
