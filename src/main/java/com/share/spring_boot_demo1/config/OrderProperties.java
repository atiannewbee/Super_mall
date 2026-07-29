package com.share.spring_boot_demo1.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 订单时效配置；无效的待支付时长回退为 30 分钟。
 */
@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(Duration pendingPaymentTtl) {
    public OrderProperties {
        if (pendingPaymentTtl == null || pendingPaymentTtl.isNegative() || pendingPaymentTtl.isZero()) {
            pendingPaymentTtl = Duration.ofMinutes(30);
        }
    }
}
