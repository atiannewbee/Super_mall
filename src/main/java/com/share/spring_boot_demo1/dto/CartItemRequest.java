package com.share.spring_boot_demo1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 添加购物车商品请求。
 */
public record CartItemRequest(
        @NotBlank @Size(max = 80) String skuCode,
        @Min(1) @Max(99) int quantity
) {
}
