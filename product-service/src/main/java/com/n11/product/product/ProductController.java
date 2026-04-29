package com.n11.product.product;

import com.n11.product.product.dto.CreateProductRequest;
import com.n11.product.product.dto.ProductDetailDto;
import com.n11.product.product.dto.ProductSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Catalog REST surface. Reads (GET /products, GET /products/{id}) are public per
 * api-contracts.md §3. Writes (POST/PUT/DELETE) require ROLE_ADMIN — gateway
 * also gates the path in plan 04-03; this is the defense-in-depth service-layer
 * check via the gateway-injected X-User-Roles header.
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private static final String HEADER_USER_ROLES = "X-User-Roles";

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Paginated product listing",
               description = "Page is 0-indexed (page 0 = first page). Default size = 20. " +
                             "Query params: q (text search, ILIKE on lower(name_tr)), categoryId. " +
                             "Sort examples: sort=priceGross,asc | sort=createdAt,desc | sort=nameTr,asc")
    @GetMapping
    public Page<ProductSummaryDto> list(
            @ParameterObject @PageableDefault(size = 20, sort = "created_at",
                    direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId) {
        return productService.search(q, categoryId, pageable);
    }

    @Operation(summary = "Product detail (PDP)")
    @GetMapping("/{id}")
    public ProductDetailDto get(@PathVariable UUID id) {
        return productService.getDetail(id);
    }

    @Operation(summary = "Create product (ROLE_ADMIN required)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDetailDto create(HttpServletRequest request,
                                   @Valid @RequestBody CreateProductRequest body) {
        requireAdmin(request);
        return productService.create(body);
    }

    @Operation(summary = "Update product (ROLE_ADMIN required)")
    @PutMapping("/{id}")
    public ProductDetailDto update(HttpServletRequest request,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody CreateProductRequest body) {
        requireAdmin(request);
        return productService.update(id, body);
    }

    @Operation(summary = "Delete product (ROLE_ADMIN required)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest request, @PathVariable UUID id) {
        requireAdmin(request);
        productService.delete(id);
    }

    // Defense-in-depth admin gate (orchestrator-locked):
    // Reads X-User-Roles header injected by api-gateway after JWT validation.
    // Returns 403 if header is missing or does not contain ROLE_ADMIN.
    private void requireAdmin(HttpServletRequest request) {
        String roles = request.getHeader(HEADER_USER_ROLES);
        if (roles == null || !roles.contains("ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Yetkisiz erişim");
        }
    }
}
