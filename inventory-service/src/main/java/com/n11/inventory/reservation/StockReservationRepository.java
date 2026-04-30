package com.n11.inventory.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    /**
     * Check if a reservation already exists for the given order+product pair.
     * Used for secondary idempotency guard in addition to the processed_events check.
     */
    boolean existsByOrderIdAndProductId(UUID orderId, UUID productId);

    /**
     * Find all reservations for a given order with the specified status.
     * Used by compensation consumers (PaymentFailed, OrderCancelled) to release stock.
     */
    java.util.List<StockReservation> findByOrderIdAndStatus(UUID orderId, String status);
}
