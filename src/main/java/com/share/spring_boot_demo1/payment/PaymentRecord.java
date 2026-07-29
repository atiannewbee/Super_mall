package com.share.spring_boot_demo1.payment;

import com.share.spring_boot_demo1.dto.PaymentResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 支付单持久化快照，在业务层和渠道适配层之间传递。
 */
public record PaymentRecord(
        long id,
        String paymentNo,
        long orderId,
        long userId,
        String orderNo,
        String channel,
        String status,
        BigDecimal amount,
        String currency,
        String providerTradeNo,
        LocalDateTime paidAt,
        LocalDateTime expiresAt
) {
    /**
     * 转换为渠道创建交易所需的最小模型。
     */
    public PaymentOrder toPaymentOrder() {
        return new PaymentOrder(paymentNo, orderNo, amount, currency, expiresAt);
    }

    /**
     * 转换为消费者 API 响应，并统一外部状态命名格式。
     */
    public PaymentResponse toResponse() {
        return new PaymentResponse(
                paymentNo,
                orderNo,
                channel,
                status.toLowerCase(Locale.ROOT).replace('_', '-'),
                amount,
                currency,
                paidAt
        );
    }
}
