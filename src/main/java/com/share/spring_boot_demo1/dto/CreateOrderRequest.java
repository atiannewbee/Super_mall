package com.share.spring_boot_demo1.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建订单请求；商品与金额从服务端购物车读取。
 */
public record CreateOrderRequest(
        @NotNull Long addressId,
        @Size(max = 255) String buyerNote,
        @Pattern(regexp = "^(ALIPAY|WECHAT_PAY|UNION_PAY)$", message = "不支持该支付方式") String paymentChannel,
        boolean invoiceRequired
) {
}
