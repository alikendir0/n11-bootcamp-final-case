package com.n11.inventory.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {
    // No extra queries needed for Phase 4 — findById and save are sufficient.
}
