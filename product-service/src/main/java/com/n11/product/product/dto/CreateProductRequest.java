package com.n11.product.product.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank(message = "SKU zorunludur")
        String sku,

        @NotBlank(message = "Ürün adı zorunludur")
        @Size(max = 255, message = "Ürün adı en fazla 255 karakter olabilir")
        String nameTr,

        String descriptionTr,

        @NotNull(message = "Fiyat zorunludur")
        @DecimalMin(value = "0.00", inclusive = true, message = "Fiyat 0'dan küçük olamaz")
        BigDecimal priceGross,

        @DecimalMin(value = "0.00")
        @DecimalMax(value = "100.00")
        BigDecimal kdvRate,

        UUID categoryId,

        List<String> imageUrls,

        String sellerName
) { }
