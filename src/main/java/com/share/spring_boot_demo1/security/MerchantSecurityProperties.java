package com.share.spring_boot_demo1.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 商家端独立 JWT 及初始主管账号配置。
 *
 * <p>bootstrapPassword 只用于首次建号，生产完成首次改密后应从运行环境移除。</p>
 */
@ConfigurationProperties(prefix = "app.merchant-security")
public record MerchantSecurityProperties(
        String jwtSecret,
        String jwtIssuer,
        String jwtAudience,
        Duration accessTokenTtl,
        String bootstrapEmail,
        String bootstrapPassword,
        String bootstrapName
) {
}
