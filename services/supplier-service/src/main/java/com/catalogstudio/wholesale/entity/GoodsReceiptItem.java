package com.catalogstudio.wholesale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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
@Table(name = "goods_receipt_items")
public class GoodsReceiptItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "receipt_id", nullable = false, updatable = false)
    private Long receiptId;

    @Column(name = "order_item_id", nullable = false, updatable = false)
    private Long orderItemId;

    @Column(length = 120)
    private String sku;

    @Column(name = "product_title", nullable = false)
    private String productTitle;

    @Column(name = "ordered_qty", nullable = false)
    private int orderedQty;

    @Column(name = "received_qty", nullable = false)
    private int receivedQty;

    @Column(name = "missing_qty", nullable = false)
    private int missingQty;

    @Column(name = "damaged_qty", nullable = false)
    private int damagedQty;

    @Column(name = "rejected_qty", nullable = false)
    private int rejectedQty;

    @Column(name = "accepted_qty", nullable = false)
    private int acceptedQty;

    @PrePersist
    void prePersist() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
    }
}
