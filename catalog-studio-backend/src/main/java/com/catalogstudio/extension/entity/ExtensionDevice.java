package com.catalogstudio.extension.entity;

import com.catalogstudio.user.entity.User;
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
@Table(name = "extension_devices")
public class ExtensionDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_name", nullable = false, length = 160)
    private String deviceName;

    @Column(name = "extension_key_hash", nullable = false, unique = true)
    private String extensionKeyHash;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "locked_shop_name", length = 191)
    private String lockedShopName;

    @Column(name = "locked_shop_uid", length = 80)
    private String lockedShopUid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeviceStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (status == null) {
            status = DeviceStatus.ACTIVE;
        }
        createdAt = Instant.now();
    }

    public enum DeviceStatus {
        ACTIVE, REVOKED
    }
}
