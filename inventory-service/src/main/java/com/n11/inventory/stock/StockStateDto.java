package com.n11.inventory.stock;

import java.util.UUID;

/**
 * Response DTO for GET /inventory/{productId}.
 *
 * <p>Stock state label mapping (PROD-06):
 * <ul>
 *   <li>effectiveAvailable &lt;= 0 → TUKENDI / "Tükendi"</li>
 *   <li>0 &lt; effectiveAvailable &lt;= lowStockThreshold → SON_URUN / "Son N ürün!"</li>
 *   <li>effectiveAvailable &gt; lowStockThreshold → STOKTA / "Stokta"</li>
 * </ul>
 */
public record StockStateDto(
        UUID productId,
        int availableQty,
        String stockState,
        String stockStateLabel,
        int displayQty) {

    /**
     * Build a StockStateDto from a Stock entity.
     *
     * @param stock the Stock entity (must be non-null)
     * @return populated DTO with Turkish stock label
     */
    public static StockStateDto from(Stock stock) {
        int eff = stock.getEffectiveAvailable();
        String state;
        String label;
        if (eff <= 0) {
            state = "TUKENDI";
            label = "Tükendi";
        } else if (eff <= stock.getLowStockThreshold()) {
            state = "SON_URUN";
            label = "Son " + eff + " ürün!";
        } else {
            state = "STOKTA";
            label = "Stokta";
        }
        return new StockStateDto(stock.getProductId(), eff, state, label, eff);
    }

    /**
     * Fallback DTO for unknown product IDs — returns TUKENDI (graceful degradation).
     * Avoids a 404 for products not yet seeded in inventory.
     *
     * @param productId the requested product UUID
     * @return TUKENDI DTO
     */
    public static StockStateDto notFound(UUID productId) {
        return new StockStateDto(productId, 0, "TUKENDI", "Tükendi", 0);
    }
}
