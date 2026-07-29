package com.share.spring_boot_demo1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 更新购物车数量或选中状态的请求。
 */
public record CartItemUpdateRequest(
        @Min(1) @Max(99) Integer quantity,
        Boolean selected
) {
}
