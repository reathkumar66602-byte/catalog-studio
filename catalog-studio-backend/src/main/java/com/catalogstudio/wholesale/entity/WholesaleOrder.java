package com.catalogstudio.wholesale.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "wholesale_orders")
public class WholesaleOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @Column(name = "buyer_user_id", updatable = false)
    private Long buyerUserId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PLACED;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Source source = Source.ONLINE;

    @Column(name = "buyer_note", columnDefinition = "text")
    private String buyerNote;

    @Column(name = "supplier_note", columnDefinition = "text")
    private String supplierNote;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "accepted_total", precision = 14, scale = 2)
    private BigDecimal acceptedTotal;

    @Column(name = "buyer_name", length = 160)
    private String buyerName;

    @Column(name = "buyer_email")
    private String buyerEmail;

    @Column(name = "buyer_mobile", length = 20)
    private String buyerMobile;

    @Column(name = "buyer_gstin", length = 20)
    private String buyerGstin;

    @Column(name = "buyer_address", length = 500)
    private String buyerAddress;

    @Column(name = "buyer_mobile_updated_at")
    private Instant buyerMobileUpdatedAt;

    @Column(name = "bill_pdf_url", length = 500)
    private String billPdfUrl;

    @Column(name = "bill_pdf_storage_key", length = 255)
    private String billPdfStorageKey;

    @Column(name = "bill_emailed_at")
    private Instant billEmailedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Builder.Default
    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "taxable_amount", precision = 14, scale = 2)
    private BigDecimal taxableAmount;

    @Builder.Default
    @Column(name = "cgst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "sgst_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(name = "gst_percent", precision = 5, scale = 2)
    private BigDecimal gstPercent;

    @Column(name = "grand_total", precision = 14, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (placedAt == null) {
            placedAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public enum Status {
        PLACED, ACCEPTED, REJECTED, CANCELLED, FULFILLED
    }

    public enum Source {
        ONLINE, MANUAL
    }
}