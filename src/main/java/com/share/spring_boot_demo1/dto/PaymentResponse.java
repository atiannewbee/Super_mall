package com.share.spring_boot_demo1.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付记录响应。
 */
public record PaymentResponse(
        String paymentNo,
        String orderNo,
        String channel,
        String status,
        BigDecimal amount,
        String currency,
        LocalDateTime paidAt
) {
}
