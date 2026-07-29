package com.share.spring_boot_demo1.dto;

import java.math.BigDecimal;

/**
 * 商家工作台关键经营与履约指标。
 */
public record MerchantDashboardResponse(
        long pendingPaymentOrders,
        long unfulfilledOrders,
        long pickingOrders,
        long shippedToday,
        long lowStockSkus,
        BigDecimal todayPaidAmount
) {
}
