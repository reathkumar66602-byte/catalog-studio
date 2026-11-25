package com.catalogstudio.wholesale.service;

/**
 * Accepted units are received goods that are neither damaged nor rejected.
 * Missing units never enter sellable inventory.
 */
public final class GoodsReceiptMath {

    private GoodsReceiptMath() {}

    public record Line(int ordered, int received, int damaged, int rejected, int missing, int accepted) {}

    public static Line compute(int ordered, int received, int damaged, int rejected) {
        if (ordered <= 0) {
            throw new IllegalArgumentException("Ordered quantity must be positive");
        }
        if (received < 0 || damaged < 0 || rejected < 0) {
            throw new IllegalArgumentException("Quantities cannot be negative");
        }
        if (received > ordered) {
            throw new IllegalArgumentException("Received quantity cannot exceed the ordered quantity");
        }
        if (damaged + rejected > received) {
            throw new IllegalArgumentException("Damaged and rejected quantities cannot exceed the received quantity");
        }
        return new Line(ordered, received, damaged, rejected, ordered - received, received - damaged - rejected);
    }
}
