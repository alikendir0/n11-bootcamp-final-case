package com.n11.product.product.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummaryDto(
        UUID id,
        String sku,
        String nameTr,
        BigDecimal priceGross,
        BigDecimal kdvRate,
        String slug,
        String categoryName,
        String firstImageUrl
) { }
