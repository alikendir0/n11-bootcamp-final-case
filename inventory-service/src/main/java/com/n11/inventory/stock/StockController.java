package com.n11.inventory.stock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST surface for stock state queries (PROD-06, PROD-07).
 *
 * <p>GET /inventory/{productId} is public — no auth required.
 * The response includes Turkish stock-state labels ("Stokta", "Tükendi", "Son N ürün!")
 * for direct display in the frontend without client-side mapping.
 */
@RestController
@RequestMapping("/inventory")
@Tag(name = "Inventory", description = "Stock state queries for product pages")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @Operation(
        summary = "Stock state for product",
        description = "Returns stock state with Turkish labels. " +
                      "stockState is one of: STOKTA, SON_URUN, TUKENDI. " +
                      "Unknown productId returns TUKENDI (graceful degradation)."
    )
    @GetMapping("/{productId}")
    public StockStateDto get(@PathVariable UUID productId) {
        return stockService.getStockState(productId);
    }
}
