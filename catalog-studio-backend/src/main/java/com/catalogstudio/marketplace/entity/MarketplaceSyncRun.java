package com.catalogstudio.marketplace.entity;

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
@Table(name = "marketplace_sync_runs")
public class MarketplaceSyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private MarketplaceConnection connection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 40)
    private String marketplace;

    @Column(name = "sync_type", nullable = false, length = 32)
    private String syncType;

    @Column(nullable = false, length = 32)
    private String status;

    @Builder.Default
    @Column(name = "items_ok", nullable = false)
    private Integer itemsOk = 0;

    @Builder.Default
    @Column(name = "items_failed", nullable = false)
    private Integer itemsFailed = 0;

    @Column
    private String message;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    void prePersist() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (marketplace == null) {
            marketplace = "MEESHO";
        }
        if (status == null) {
            status = "RUNNING";
        }
        if (itemsOk == null) {
            itemsOk = 0;
        }
        if (itemsFailed == null) {
            itemsFailed = 0;
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}
