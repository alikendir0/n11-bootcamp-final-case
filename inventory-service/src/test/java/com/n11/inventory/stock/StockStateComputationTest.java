package com.n11.inventory.stock;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit test — no Spring context, no DB.
 * Validates StockStateDto.from(stock) computes the correct Turkish stock labels
 * for all three states and the threshold boundary.
 */
class StockStateComputationTest {

    @Test
    void zeroAvailableQty_isTukendi() {
        Stock stock = new Stock(UUID.randomUUID(), 0, 5);
        StockStateDto dto = StockStateDto.from(stock);

        assertEquals(0, dto.availableQty());
        assertEquals("TUKENDI", dto.stockState());
        assertTrue(dto.stockStateLabel().contains("Tükendi"),
                "Expected label to contain 'Tükendi' but was: " + dto.stockStateLabel());
    }

    @Test
    void belowThreshold_isSonUrun() {
        Stock stock = new Stock(UUID.randomUUID(), 3, 5);
        StockStateDto dto = StockStateDto.from(stock);

        assertEquals(3, dto.availableQty());
        assertEquals("SON_URUN", dto.stockState());
        assertEquals("Son 3 ürün!", dto.stockStateLabel());
    }

    @Test
    void aboveThreshold_isStokta() {
        Stock stock = new Stock(UUID.randomUUID(), 10, 5);
        StockStateDto dto = StockStateDto.from(stock);

        assertEquals(10, dto.availableQty());
        assertEquals("STOKTA", dto.stockState());
        assertEquals("Stokta", dto.stockStateLabel());
    }

    @Test
    void exactlyAtThreshold_isSonUrun() {
        // effectiveAvailable == threshold -> SON_URUN (<=)
        Stock stock = new Stock(UUID.randomUUID(), 5, 5);
        StockStateDto dto = StockStateDto.from(stock);

        assertEquals(5, dto.availableQty());
        assertEquals("SON_URUN", dto.stockState());
        assertEquals("Son 5 ürün!", dto.stockStateLabel());
    }
}
