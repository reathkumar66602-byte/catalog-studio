package com.catalogstudio.wholesale.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GoodsReceiptMathTest {

    @Test
    void acceptedExcludesMissingDamagedAndRejected() {
        GoodsReceiptMath.Line line = GoodsReceiptMath.compute(10, 8, 1, 2);
        assertEquals(2, line.missing());
        assertEquals(5, line.accepted());
    }

    @Test
    void rejectsReceivedAboveOrdered() {
        assertThrows(IllegalArgumentException.class, () -> GoodsReceiptMath.compute(4, 5, 0, 0));
    }

    @Test
    void rejectsDamagedAboveReceived() {
        assertThrows(IllegalArgumentException.class, () -> GoodsReceiptMath.compute(10, 4, 3, 2));
    }
}
