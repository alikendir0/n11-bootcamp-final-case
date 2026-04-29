package com.n11.product.category;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Top-level Turkish category navigation (PROD-03). Public endpoint — no auth.
 */
@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Operation(summary = "Top-level categories ordered by sort_order")
    @GetMapping
    public List<Category> list() {
        return categoryRepository.findByParentIsNullOrderBySortOrderAsc();
    }
}
