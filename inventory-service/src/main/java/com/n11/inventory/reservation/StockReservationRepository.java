package com.n11.inventory.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    /**
     * Check if a reservation already exists for the given order+product pair.
     * Used for secondary idempotency guard in addition to the processed_events check.
     */
    boolean existsByOrderIdAndProductId(UUID orderId, UUID productId);
}
