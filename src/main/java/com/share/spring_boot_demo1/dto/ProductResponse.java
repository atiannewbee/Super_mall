package com.share.spring_boot_demo1.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品目录聚合响应，包含图片、卖点、SKU 与规格选项。
 */
public record ProductResponse(
        Long id,
        String slug,
        String name,
        String brand,
        String categoryId,
        String tagline,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        BigDecimal rating,
        long reviewCount,
        long soldCount,
        String badge,
        String accent,
        String image,
        List<String> gallery,
        List<String> features,
        boolean isFeatured,
        boolean isNew,
        boolean isDeal,
        List<Sku> skus
) {
    public ProductResponse {
        gallery = List.copyOf(gallery);
        features = List.copyOf(features);
        skus = List.copyOf(skus);
    }

    public record Sku(
            Long id,
            String skuCode,
            String label,
            BigDecimal price,
            BigDecimal originalPrice,
            int stock,
            Map<String, String> options
    ) {
        public Sku {
            options = Map.copyOf(options);
        }
    }
}
