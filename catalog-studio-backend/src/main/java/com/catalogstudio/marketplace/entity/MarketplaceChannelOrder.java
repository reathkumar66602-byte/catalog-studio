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
@Table(name = "marketplace_channel_orders")
public class MarketplaceChannelOrder {

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

    @Column(name = "external_order_id", nullable = false, length = 120)
    private String externalOrderId;

    @Builder.Default
    @Column(name = "external_sub_order_id", nullable = false, length = 120)
    private String externalSubOrderId = "";

    @Column(nullable = false, length = 40)
    private String status;

    @Column(name = "channel_status", length = 40)
    private String channelStatus;

    @Column(length = 120)
    private String sku;

    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal price;

    @Builder.Default
    @Column(length = 8)
    private String currency = "INR";

    @Column(name = "courier_code", length = 40)
    private String courierCode;

    @Column(name = "tracking_url")
    private String trackingUrl;

    @Builder.Default
    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "buyer_name", length = 160)
    private String buyerName;

    @Column(name = "shipping_city", length = 120)
    private String shippingCity;

    @Column(length = 80)
    private String awb;

    @Column(name = "label_url")
    private String labelUrl;

    @Builder.Default
    @Column(name = "label_status", nullable = false, length = 32)
    private String labelStatus = "NONE";

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rawJson = Map.of();

    @Column(name = "ordered_at")
    private Instant orderedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

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
        if (marketplace == null) {
            marketplace = "MEESHO";
        }
        if (externalSubOrderId == null) {
            externalSubOrderId = "";
        }
        if (status == null) {
            status = "PENDING";
        }
        if (labelStatus == null) {
            labelStatus = "NONE";
        }
        if (quantity == null) {
            quantity = 1;
        }
        if (rawJson == null) {
            rawJson = Map.of();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
