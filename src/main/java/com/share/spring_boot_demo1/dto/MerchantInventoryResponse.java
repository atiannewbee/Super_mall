package com.share.spring_boot_demo1.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家 SKU 库存视图，区分可售、预占和已售数量。
 */
public record MerchantInventoryResponse(
        long skuId,
        long productId,
        long categoryId,
        String productName,
        String productSlug,
        String tagline,
        String description,
        String productStatus,
        String skuCode,
        String skuLabel,
        String image,
        BigDecimal price,
        BigDecimal originalPrice,
        int availableQuantity,
        int lockedQuantity,
        long soldQuantity,
        boolean lowStock,
        LocalDateTime updatedAt
) {
}
