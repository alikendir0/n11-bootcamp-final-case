package com.n11.inventory.stock;

import com.n11.inventory.reservation.StockReservation;
import com.n11.inventory.reservation.StockReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Core inventory domain service.
 *
 * <p>Reads (getStockState) are @Transactional(readOnly=true) to allow Hibernate
 * to skip dirty-checking and to route to read replicas if configured.
 */
@Service
public class StockService {

    private final StockRepository stockRepository;
    private final StockReservationRepository reservationRepository;

    public StockService(StockRepository stockRepository,
                        StockReservationRepository reservationRepository) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
    }

    /**
     * Returns the stock state DTO for the given product.
     * Returns {@link StockStateDto#notFound(UUID)} (TUKENDI) for unknown products
     * rather than 404 — graceful degradation for products not yet seeded.
     */
    @Transactional(readOnly = true)
    public StockStateDto getStockState(UUID productId) {
        return stockRepository.findById(productId)
                .map(StockStateDto::from)
                .orElseGet(() -> StockStateDto.notFound(productId));
    }

    /**
     * Reserve {@code qty} units for the given order+product pair.
     * Saves the stock row (triggering @Version check) and persists the reservation.
     *
     * <p>Callers (OrderCreatedConsumer) must catch:
     * <ul>
     *   <li>{@link InsufficientStockException} — not enough effective available qty</li>
     *   <li>{@link org.springframework.orm.ObjectOptimisticLockingFailureException} — concurrent reservation</li>
     * </ul>
     *
     * @param orderId   the saga order ID
     * @param productId the product to reserve
     * @param qty       units to reserve (must be > 0)
     * @return the persisted StockReservation
     * @throws InsufficientStockException if effectiveAvailable < qty
     */
    @Transactional
    public StockReservation reserveStock(UUID orderId, UUID productId, int qty) {
        Stock stock = stockRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (stock.getEffectiveAvailable() < qty) {
            throw new InsufficientStockException(productId, qty, stock.getEffectiveAvailable());
        }

        stock.reserve(qty);
        stockRepository.save(stock);  // @Version check happens here

        StockReservation reservation = new StockReservation(orderId, productId, qty);
        return reservationRepository.save(reservation);
    }
}
