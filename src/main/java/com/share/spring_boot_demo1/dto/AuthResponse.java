package com.share.spring_boot_demo1.dto;

/**
 * 消费者登录结果，包含 Bearer 令牌及当前用户摘要。
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserProfileResponse user
) {
}
