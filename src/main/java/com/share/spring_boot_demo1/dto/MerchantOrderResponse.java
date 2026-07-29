package com.share.spring_boot_demo1.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家侧订单聚合响应，包含履约、物流、状态历史和审计记录。
 */
public record MerchantOrderResponse(
        long id,
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
        Recipient recipient,
        List<Item> items,
        Shipment shipment,
        List<TimelineEvent> timeline,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime shippedAt
) {
    public MerchantOrderResponse {
        items = List.copyOf(items);
        timeline = List.copyOf(timeline);
    }

    public record Recipient(
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
            long id,
            Long productId,
            Long skuId,
            String productSlug,
            String productName,
            String skuCode,
            String skuLabel,
            String image,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineAmount
    ) {
    }

    public record Shipment(
            String shipmentNo,
            long warehouseId,
            String warehouseName,
            String carrierCode,
            String carrierName,
            String trackingNo,
            String status,
            LocalDateTime shippedAt,
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
