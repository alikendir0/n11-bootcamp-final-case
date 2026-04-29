package com.n11.product.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySlug(String slug);
    boolean existsBySku(String sku);

    // PROD-04 native ILIKE query. Uses lower(name_tr) ILIKE pattern which the
    // idx_products_name_lower_trgm GIN index accelerates (verified by EXPLAIN ANALYZE
    // in ProductSearchIntegrationTest).
    //
    // Pageable's Sort is automatically applied by Spring Data when nativeQuery=true.
    // Pitfall #24: page is 0-indexed.
    @Query(
        value = """
            SELECT * FROM products
            WHERE (:q IS NULL OR :q = '' OR lower(name_tr) ILIKE lower('%' || :q || '%'))
              AND (:categoryId IS NULL OR category_id = CAST(:categoryId AS uuid))
            """,
        countQuery = """
            SELECT count(*) FROM products
            WHERE (:q IS NULL OR :q = '' OR lower(name_tr) ILIKE lower('%' || :q || '%'))
              AND (:categoryId IS NULL OR category_id = CAST(:categoryId AS uuid))
            """,
        nativeQuery = true
    )
    Page<Product> search(@Param("q") String q,
                         @Param("categoryId") String categoryId,
                         Pageable pageable);
}
