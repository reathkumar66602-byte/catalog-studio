package com.catalogstudio.tips.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "tip_video_settings")
public class TipVideoSettings {

    public static final long DEFAULT_MAX_TOTAL_BYTES = 524_288_000L; // 500 MB
    public static final int DEFAULT_MAX_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(name = "settings_key", nullable = false, unique = true, length = 40)
    private String settingsKey = "default";

    @Builder.Default
    @Column(name = "max_count", nullable = false)
    private int maxCount = DEFAULT_MAX_COUNT;

    @Builder.Default
    @Column(name = "max_total_bytes", nullable = false)
    private long maxTotalBytes = DEFAULT_MAX_TOTAL_BYTES;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (settingsKey == null || settingsKey.isBlank()) {
            settingsKey = "default";
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
