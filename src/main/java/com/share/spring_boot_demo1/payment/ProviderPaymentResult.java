package com.share.spring_boot_demo1.payment;

import java.math.BigDecimal;

/**
 * 支付渠道查询或关闭操作的归一化结果。
 */
public record ProviderPaymentResult(
        ProviderPaymentStatus status,
        String providerTradeNo,
        BigDecimal amount,
        String failureCode,
        String failureMessage
) {
    /**
     * 创建“已关闭”结果的便捷工厂。
     */
    public static ProviderPaymentResult closed(String providerTradeNo) {
        return new ProviderPaymentResult(
                ProviderPaymentStatus.CLOSED, providerTradeNo, null, null, null
        );
    }
}
