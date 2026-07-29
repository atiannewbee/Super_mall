package com.share.spring_boot_demo1.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付宝 SDK 配置，并负责密钥文本的基础规范化。
 */
@ConfigurationProperties(prefix = "app.payment.alipay")
public record AlipayProperties(
        boolean enabled,
        String gatewayUrl,
        String appId,
        String privateKey,
        String alipayPublicKey,
        String sellerId,
        String notifyUrl,
        String returnUrl
) {
    private static final String SANDBOX_GATEWAY = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    public AlipayProperties {
        gatewayUrl = defaultIfBlank(gatewayUrl, SANDBOX_GATEWAY);
        appId = trim(appId);
        privateKey = normalizeKey(privateKey);
        alipayPublicKey = normalizeKey(alipayPublicKey);
        sellerId = trim(sellerId);
        notifyUrl = trim(notifyUrl);
        returnUrl = trim(returnUrl);
    }

    /**
     * 判断创建支付宝客户端所需配置是否完整。
     */
    public boolean configured() {
        return enabled
                && !appId.isBlank()
                && !privateKey.isBlank()
                && !alipayPublicKey.isBlank()
                && !notifyUrl.isBlank()
                && !returnUrl.isBlank();
    }

    private static String defaultIfBlank(String value, String fallback) {
        String normalized = trim(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String normalizeKey(String value) {
        return trim(value)
                .replace("\\n", "")
                .replace("\r", "")
                .replace("\n", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
