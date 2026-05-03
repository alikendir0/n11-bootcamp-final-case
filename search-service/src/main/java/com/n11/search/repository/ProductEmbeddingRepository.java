package com.n11.search.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductEmbeddingRepository extends JpaRepository<ProductEmbedding, UUID> {
}
