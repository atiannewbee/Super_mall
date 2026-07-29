package com.share.spring_boot_demo1.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发送给支付渠道的最小订单视图，不包含用户隐私和内部库存信息。
 */
public record PaymentOrder(
        String paymentNo,
        String orderNo,
        BigDecimal amount,
        String currency,
        LocalDateTime expiresAt
) {
}
