package com.share.spring_boot_demo1.payment;

import java.math.BigDecimal;

/**
 * 已完成签名和商户身份校验的支付宝异步通知。
 */
public record AlipayNotification(
        String paymentNo,
        String providerTradeNo,
        ProviderPaymentStatus status,
        BigDecimal amount,
        String notificationId,
        String eventType,
        String payloadHash
) {
}
