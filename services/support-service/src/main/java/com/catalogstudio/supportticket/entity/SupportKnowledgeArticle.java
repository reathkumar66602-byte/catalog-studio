package com.catalogstudio.supportticket.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "support_knowledge_articles")
public class SupportKnowledgeArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(nullable = false, unique = true, length = 180)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false, length = 16)
    private String locale;

    @Column(length = 64)
    private String category;

    @Column(length = 500)
    private String tags;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (uuid == null) uuid = UUID.randomUUID();
        if (locale == null || locale.isBlank()) locale = "en";
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
