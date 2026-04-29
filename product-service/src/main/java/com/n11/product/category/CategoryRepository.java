package com.n11.product.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Returns top-level categories (parent IS NULL) sorted by sort_order ASC.
    List<Category> findByParentIsNullOrderBySortOrderAsc();

    Optional<Category> findBySlug(String slug);
}
