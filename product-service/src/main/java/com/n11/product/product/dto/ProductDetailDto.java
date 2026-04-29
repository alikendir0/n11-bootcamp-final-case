package com.n11.product.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductDetailDto(
        UUID id,
        String sku,
        String nameTr,
        String descriptionTr,
        BigDecimal priceGross,
        BigDecimal kdvRate,
        String slug,
        String sellerName,
        List<String> imageUrls,
        UUID categoryId,
        String categoryName
) { }
