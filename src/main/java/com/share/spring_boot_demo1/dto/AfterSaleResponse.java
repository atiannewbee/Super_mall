package com.share.spring_boot_demo1.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 售后申请聚合响应，包含申请商品与状态时间线。
 */
public record AfterSaleResponse(
        Long id,
        String afterSaleNo,
        String orderNo,
        String type,
        String status,
        String reasonCode,
        String reasonDescription,
        BigDecimal requestedAmount,
        BigDecimal refundedAmount,
        String customerNote,
        String adminNote,
        String returnCarrier,
        String returnTrackingNo,
        List<Item> items,
        List<Event> events,
        LocalDateTime createdAt,
        LocalDateTime approvedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt
) {
    public AfterSaleResponse {
        items = List.copyOf(items);
        events = List.copyOf(events);
    }

    public record Item(
            Long id,
            Long orderItemId,
            String productName,
            String skuLabel,
            String image,
            int quantity,
            BigDecimal requestedAmount
    ) {
    }

    public record Event(
            String fromStatus,
            String toStatus,
            String description,
            String operatorType,
            LocalDateTime createdAt
    ) {
    }
}
