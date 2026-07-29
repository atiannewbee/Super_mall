package com.share.spring_boot_demo1.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地模拟支付开关；生产环境必须关闭。
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(boolean sandboxEnabled) {
}
