package com.share.spring_boot_demo1.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建真实支付后的前端启动信息。
 */
public record PaymentLaunchResponse(
        String paymentNo,
        String orderNo,
        String channel,
        String status,
        BigDecimal amount,
        String currency,
        String action,
        String launchUrl,
        LocalDateTime expiresAt
) {
}
