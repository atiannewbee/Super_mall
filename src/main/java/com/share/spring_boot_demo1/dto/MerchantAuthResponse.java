package com.share.spring_boot_demo1.dto;

/**
 * 商家登录结果，使用独立于消费者端的 Bearer 令牌。
 */
public record MerchantAuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        MerchantProfileResponse user
) {
}
