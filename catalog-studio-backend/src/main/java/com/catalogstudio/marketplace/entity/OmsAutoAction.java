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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
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
@Table(name = "oms_auto_actions")
public class OmsAutoAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "trigger_event", nullable = false, length = 64)
    private String triggerEvent;

    @Column(length = 40)
    private String channel;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> conditionJson = Map.of();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> actionJson = Map.of();

    @Column(name = "last_run_at")
    private Instant lastRunAt;

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
        if (conditionJson == null) {
            conditionJson = Map.of();
        }
        if (actionJson == null) {
            actionJson = Map.of();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
