package com.share.spring_boot_demo1.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 消费者订单聚合响应，包含成交快照、物流和状态时间线。
 */
public record OrderResponse(
        Long id,
        String orderNo,
        String status,
        String paymentStatus,
        String fulfillmentStatus,
        String afterSaleStatus,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal discount,
        BigDecimal total,
        BigDecimal paidAmount,
        String currency,
        String paymentMethod,
        String buyerNote,
        Address address,
        List<Item> items,
        Shipment shipment,
        List<TimelineEvent> timeline,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime shippedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt
) {
    public OrderResponse {
        items = List.copyOf(items);
        timeline = List.copyOf(timeline);
    }

    public record Address(
            Long addressId,
            String name,
            String phone,
            String province,
            String city,
            String district,
            String detail,
            String tag
    ) {
    }

    public record Item(
            Long id,
            Long productId,
            Long skuId,
            String productSlug,
            String productName,
            String skuCode,
            String skuLabel,
            String image,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineAmount,
            int afterSaleQuantity
    ) {
    }

    public record Shipment(
            String shipmentNo,
            String carrierCode,
            String carrierName,
            String trackingNo,
            String status,
            LocalDateTime shippedAt,
            LocalDateTime deliveredAt,
            List<ShipmentEvent> events
    ) {
        public Shipment {
            events = List.copyOf(events);
        }
    }

    public record ShipmentEvent(
            String eventCode,
            String description,
            String location,
            LocalDateTime occurredAt
    ) {
    }

    public record TimelineEvent(
            String type,
            String fromStatus,
            String toStatus,
            String note,
            String operatorType,
            LocalDateTime createdAt
    ) {
    }
}
