package com.share.spring_boot_demo1.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 库存中心首版商品表单：一个商品同时维护一个当前 SKU 和它的可售库存。
 */
public record MerchantProductRequest(
        @NotBlank @Size(max = 160) String name,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 500) String imageUrl,
        @Size(max = 255) String tagline,
        @Size(max = 5000) String description,
        @NotBlank @Pattern(regexp = "(?i)DRAFT|ACTIVE|INACTIVE") String status,
        @NotBlank @Size(max = 80)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String skuCode,
        @NotBlank @Size(max = 255) String skuLabel,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @DecimalMin("0.00") BigDecimal originalPrice,
        @NotNull @Min(0) @Max(100_000_000) Integer availableQuantity
) {
}
