package com.share.spring_boot_demo1.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车聚合响应，金额由服务端实时计算。
 */
public record CartResponse(
        List<Item> items,
        int selectedCount,
        BigDecimal selectedSubtotal
) {
    public CartResponse {
        items = List.copyOf(items);
    }

    public record Item(
            Long id,
            Long productId,
            String productSlug,
            String productName,
            String image,
            Long skuId,
            String skuCode,
            String skuLabel,
            BigDecimal price,
            int stock,
            int quantity,
            boolean selected,
            BigDecimal lineAmount
    ) {
    }
}
